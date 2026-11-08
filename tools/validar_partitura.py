#!/usr/bin/env python3
"""Revisa un archivo de partitura JSON contra el contrato (docs/CONTRATO-ARCHIVOS.md).

Uso:
    python3 tools/validar_partitura.py archivo.json [otro.json ...]

Sale con código 0 si todos los archivos están bien y 1 si hay algún error.
Solo usa la biblioteca estándar de Python. Los mensajes están en español y dicen qué corregir.
"""
import json
import re
import sys

TICKS_PER_QUARTER = 480
FORMAT_VERSION = 1
LEVELS = ("principiante", "intermedio", "avanzado")
HANDS = ("right", "left")
HAND_LABEL = {"right": "mano derecha", "left": "mano izquierda"}
PIANO_MIDI = range(21, 109)  # A0..C8

# Duraciones permitidas: base * (1, 1.5, 1.75) con hasta dos puntillos.
BASES = {"redonda": 1920, "blanca": 960, "negra": 480, "corchea": 240, "semicorchea": 120}
ALLOWED = {}
for _name, _base in BASES.items():
    ALLOWED[_base] = _name
    if _name != "redonda":
        ALLOWED[_base * 3 // 2] = _name + " con puntillo"
        ALLOWED[_base * 7 // 4] = _name + " con dos puntillos"

SCORE_KEYS = {"formatVersion", "title", "composer", "level", "goal",
              "timeSignatures", "keySignatures", "tempos", "notes", "rests"}
NOTE_KEYS = {"hand", "pitch", "startTick", "durationTicks", "tiedToNext", "finger", "tuplet"}
REST_KEYS = {"hand", "startTick", "durationTicks", "tuplet"}
TS_KEYS = {"startTick", "numerator", "denominator"}
KS_KEYS = {"startTick", "fifths"}
TEMPO_KEYS = {"startTick", "bpm"}

PITCH_RE = re.compile(r"^([A-G])(#{1,2}|b{1,2})?(-?[0-9])$")
SEMITONE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def parse_pitch(text):
    m = PITCH_RE.match(text.strip()) if isinstance(text, str) else None
    if not m:
        return None
    letter, acc, octave = m.groups()
    alter = 0 if not acc else (len(acc) if acc[0] == "#" else -len(acc))
    return (int(octave) + 1) * 12 + SEMITONE[letter] + alter


def is_int(v):
    return isinstance(v, int) and not isinstance(v, bool)


TUPLET_RE = re.compile(r"^(\d):(\d)$")


def parse_tuplet(text):
    """"3:2" -> (3, 2): 3 figuras en el tiempo de 2. Devuelve None si no es válido."""
    m = TUPLET_RE.match(text.strip()) if isinstance(text, str) else None
    if not m:
        return None
    actual, normal = int(m.group(1)), int(m.group(2))
    if not (2 <= actual <= 9 and 1 <= normal <= 8 and actual != normal):
        return None
    return actual, normal


def written_ticks(duration, tuplet):
    """Duración de la figura tal como se escribe (None si el grupo no da una figura entera)."""
    if tuplet is None:
        return duration
    actual, normal = tuplet
    return duration * actual // normal if (duration * actual) % normal == 0 else None


def duration_name(ticks):
    return ALLOWED.get(ticks)


def allowed_list():
    return ", ".join(str(t) for t in sorted(ALLOWED))


def check_fields(obj, allowed, where, errors):
    """Claves desconocidas = error (la app rechaza el archivo)."""
    for key in obj:
        if key not in allowed:
            errors.append(f"{where}: la clave \"{key}\" no existe en el modelo (la app rechaza el archivo). "
                          f"Claves válidas: {', '.join(sorted(allowed))}.")


def load_list(data, key, keys, errors):
    value = data.get(key)
    if value is None:
        return []
    if not isinstance(value, list):
        errors.append(f"\"{key}\" tiene que ser una lista.")
        return []
    items = []
    for i, item in enumerate(value, 1):
        if not isinstance(item, dict):
            errors.append(f"{key} #{i}: tiene que ser un objeto con llaves {{ }}.")
            continue
        check_fields(item, keys, f"{key} #{i}", errors)
        items.append(dict(item))  # copia: el validador anota datos auxiliares sin tocar la entrada
    return items


def validate(data):
    """Devuelve la lista de problemas (vacía = el archivo cumple el contrato)."""
    errors = []
    if not isinstance(data, dict):
        return ["El archivo tiene que ser un objeto JSON con llaves { }."]
    check_fields(data, SCORE_KEYS, "Partitura", errors)

    # --- Datos generales ---
    if "formatVersion" in data and data["formatVersion"] != FORMAT_VERSION:
        errors.append(f"formatVersion tiene que ser {FORMAT_VERSION} (es {data['formatVersion']!r}).")
    title = data.get("title")
    if not isinstance(title, str) or not title.strip():
        errors.append("Falta el título (\"title\"): obligatorio, corto y en español.")
    level = data.get("level")
    if level is None:
        errors.append("Falta \"level\": \"principiante\", \"intermedio\" o \"avanzado\".")
    elif level not in LEVELS:
        errors.append(f"\"level\" tiene que ser \"principiante\", \"intermedio\" o \"avanzado\" (es {level!r}).")
    goal = data.get("goal")
    if not isinstance(goal, str) or not goal.strip():
        errors.append("Falta \"goal\": una frase de qué se practica con la pieza.")

    # --- Firmas de compás, armaduras y tempos ---
    sigs = load_list(data, "timeSignatures", TS_KEYS, errors)
    keys = load_list(data, "keySignatures", KS_KEYS, errors)
    tempos = load_list(data, "tempos", TEMPO_KEYS, errors)
    if not sigs:
        sigs = [{"startTick": 0, "numerator": 4, "denominator": 4}]
    if not keys:
        keys = [{"startTick": 0, "fifths": 0}]
    if not tempos:
        tempos = [{"startTick": 0, "bpm": 100.0}]

    def check_starts(name, items):
        starts = [it.get("startTick") for it in items]
        if any(not is_int(s) or s < 0 for s in starts):
            errors.append(f"{name}: cada \"startTick\" tiene que ser un entero de 0 o más.")
            return False
        if starts[0] != 0:
            errors.append(f"{name}: el primero tiene que estar en el tick 0.")
        if any(b <= a for a, b in zip(starts, starts[1:])):
            errors.append(f"{name}: tienen que estar ordenados y sin repetir posición.")
        return True

    sigs_ok = check_starts("Firmas de compás", sigs)
    keys_ok = check_starts("Armaduras", keys)
    tempos_ok = check_starts("Tempos", tempos)

    for s in sigs:
        n, d = s.get("numerator"), s.get("denominator")
        if not is_int(n) or n < 1:
            errors.append(f"Firma de compás: \"numerator\" tiene que ser un entero de 1 o más (es {n!r}).")
            sigs_ok = False
        if d not in (1, 2, 4, 8, 16, 32):
            errors.append(f"Firma de compás: \"denominator\" tiene que ser 1, 2, 4, 8, 16 o 32 (es {d!r}).")
            sigs_ok = False
    for k in keys:
        f = k.get("fifths")
        if not is_int(f) or not -7 <= f <= 7:
            errors.append(f"Armadura: \"fifths\" tiene que ser un entero de -7 a 7 (es {f!r}).")
            keys_ok = False
    for t in tempos:
        b = t.get("bpm")
        if isinstance(b, bool) or not isinstance(b, (int, float)) or b <= 0:
            errors.append(f"Tempo: \"bpm\" tiene que ser un número mayor que 0 (es {b!r}). "
                          "Va en negras por minuto (un 6/8 \"negra con puntillo = 60\" se escribe 90).")
            tempos_ok = False
        elif b < 20 or b > 300:
            errors.append(f"Tempo: {b} bpm es muy raro. ¿Está en negras por minuto? (regla 9)")

    # --- Notas y silencios ---
    raw_notes = load_list(data, "notes", NOTE_KEYS, errors)
    raw_rests = load_list(data, "rests", REST_KEYS, errors)
    notes, rests = [], []

    def basic(item, where, kind):
        ok = True
        if item.get("hand") not in HANDS:
            errors.append(f"{where}: \"hand\" tiene que ser \"right\" o \"left\" (es {item.get('hand')!r}).")
            ok = False
        for key in ("startTick", "durationTicks"):
            if not is_int(item.get(key)):
                errors.append(f"{where}: \"{key}\" tiene que ser un entero.")
                ok = False
        if not ok:
            return False
        if item["startTick"] < 0:
            errors.append(f"{where}: el inicio no puede ser negativo.")
            return False
        if item["durationTicks"] <= 0:
            errors.append(f"{where}: la duración tiene que ser mayor que 0.")
            return False
        tuplet = None
        if item.get("tuplet") is not None:
            tuplet = parse_tuplet(item["tuplet"])
            if tuplet is None:
                errors.append(f"{where}: \"tuplet\" tiene que escribirse como \"3:2\" (3 figuras en el tiempo de 2), "
                              f"no {item['tuplet']!r}.")
                return False
        item["_tuplet"] = tuplet
        d = item["durationTicks"]
        if tuplet is None:
            if d not in ALLOWED:
                errors.append(f"{where}: la duración {d} no es una figura que la app sepa dibujar. Si es un tresillo o "
                              f"similar, agregá \"tuplet\": \"3:2\" (3 en el tiempo de 2) y dejá la duración real en ticks. "
                              f"Duraciones comunes válidas: {allowed_list()}.")
        else:
            w = written_ticks(d, tuplet)
            if w is None or w not in ALLOWED:
                errors.append(f"{where}: con el grupo {item['tuplet']} no puede durar {d} ticks: la figura escrita "
                              "tendría que ser redonda, blanca, negra, corchea o semicorchea (con hasta dos puntillos).")
        return True

    for i, n in enumerate(raw_notes, 1):
        where = f"Nota #{i}"
        pitch = parse_pitch(n.get("pitch"))
        if pitch is None:
            errors.append(f"{where}: altura inválida {n.get('pitch')!r}. Se escribe como C4, F#4, Bb3 "
                          "(letra A-G, alteración opcional y octava).")
            basic(n, where, "nota")
            continue
        where = f"Nota {n['pitch']} #{i}"
        if not basic(n, where, "nota"):
            continue
        if pitch not in PIANO_MIDI:
            errors.append(f"{where}: fuera del rango del piano (de A0 a C8). Revisá la octava: el Do central es C4.")
        tied = n.get("tiedToNext", False)
        if not isinstance(tied, bool):
            errors.append(f"{where}: \"tiedToNext\" tiene que ser true o false.")
            tied = False
        finger = n.get("finger")
        if finger is not None and (not is_int(finger) or not 1 <= finger <= 5):
            errors.append(f"{where}: \"finger\" tiene que ser un entero de 1 (pulgar) a 5 (meñique).")
        notes.append({**n, "midi": pitch, "tied": tied, "i": i})
    for i, r in enumerate(raw_rests, 1):
        if basic(r, f"Silencio #{i}", "silencio"):
            rests.append(r)

    if not notes:
        errors.append("La partitura no tiene notas.")

    fingered = [n for n in notes if n.get("finger") is not None]
    if fingered and len(fingered) != len(notes):
        errors.append("Digitación a medias: \"finger\" está en algunas notas pero no en todas. "
                      "Si se escribe, que sea para todas las notas.")

    if not (sigs_ok and keys_ok and tempos_ok) or not notes:
        return errors  # sin esto no se pueden calcular los compases

    # --- Compases ---
    end = max([n["startTick"] + n["durationTicks"] for n in notes] +
              [r["startTick"] + r["durationTicks"] for r in rests])
    measures = []  # (numero, inicio, fin)
    tick, number = 0, 1
    while tick < end:
        sig = [s for s in sigs if s["startTick"] <= tick][-1]
        length = sig["numerator"] * 4 * TICKS_PER_QUARTER // sig["denominator"]
        measures.append((number, tick, tick + length))
        tick += length
        number += 1
    starts = {m[1] for m in measures}
    for name, items, what in (("firma de compás", sigs, "cambio de compás"), ("armadura", keys, "cambio de armadura")):
        for it in items:
            if it["startTick"] < end and it["startTick"] not in starts:
                errors.append(f"El {what} del tick {it['startTick']} no cae al inicio de un compás.")

    def measure_of(t):
        return next(m for m in measures if m[1] <= t < m[2])

    def where_note(n):
        return f"La nota {n['pitch']} ({HAND_LABEL[n['hand']]}, compás {measure_of(n['startTick'])[0]})"

    # --- Barras de compás ---
    for n in notes:
        m = measure_of(n["startTick"])
        if n["startTick"] + n["durationTicks"] > m[2]:
            errors.append(f"{where_note(n)} cruza la barra de compás: partila en dos y unilas con \"tiedToNext\": true (regla 4).")
    for r in rests:
        m = measure_of(r["startTick"])
        if r["startTick"] + r["durationTicks"] > m[2]:
            errors.append(f"Un silencio de la {HAND_LABEL[r['hand']]} en el compás {m[0]} cruza la barra de compás: partilo en dos.")

    # --- Ligaduras ---
    for n in notes:
        if n["tied"]:
            end_tick = n["startTick"] + n["durationTicks"]
            if not any(o["hand"] == n["hand"] and o["midi"] == n["midi"] and o["startTick"] == end_tick for o in notes):
                errors.append(f"{where_note(n)} está ligada, pero no hay una nota igual (misma mano y altura) "
                              "que empiece justo donde termina.")

    # --- Grupos irregulares: acordes coherentes y grupos completos ---
    chord_tuplets = {}
    for n in notes:
        chord_tuplets.setdefault((n["hand"], n["startTick"], n["durationTicks"]), set()).add(n.get("_tuplet"))
    for (hand, start, _), group in chord_tuplets.items():
        if len(group) > 1:
            errors.append(f"Las notas del acorde de la {HAND_LABEL[hand]} del compás {measure_of(start)[0]} "
                          "tienen que tener el mismo grupo irregular (\"tuplet\").")
    for hand in HANDS:
        figs = {}
        for n in notes:
            if n["hand"] == hand:
                figs[(n["startTick"], n["durationTicks"])] = n.get("_tuplet")
        for r in rests:
            if r["hand"] == hand:
                figs.setdefault((r["startTick"], r["durationTicks"]), r.get("_tuplet"))
        run = []

        def close_run():
            if run and run[0][2] is not None:
                actual, normal = run[0][2]
                smallest = min(written_ticks(d, run[0][2]) or d for _, d, _ in run)
                total = sum(d for _, d, _ in run)
                if total % (normal * smallest) != 0:
                    errors.append(f"Un grupo irregular {actual}:{normal} de la {HAND_LABEL[hand]} en el compás "
                                  f"{measure_of(run[0][0])[0]} está incompleto: las figuras suman {total} ticks y tendrían "
                                  f"que formar grupos enteros (de {normal * smallest} ticks).")
            run.clear()

        for (start, dur) in sorted(figs):
            t = figs[(start, dur)]
            if not (run and run[-1][2] is not None and run[-1][2] == t
                    and run[-1][0] + run[-1][1] == start and start not in starts):
                close_run()
            run.append((start, dur, t))
        close_run()

    # --- Cada mano: una figura por vez, llenando cada compás ---
    for hand in HANDS:
        # Figuras: acordes (mismo inicio y duración) o silencios.
        figures = {}  # (inicio, duración) -> "nota"/"silencio"
        bad = False
        for n in [n for n in notes if n["hand"] == hand]:
            key = (n["startTick"], n["durationTicks"])
            if figures.get(key) == "silencio":
                errors.append(f"{where_note(n)} coincide con un silencio de la misma mano: quitá el silencio.")
                bad = True
            figures[key] = "nota"
        for r in [r for r in rests if r["hand"] == hand]:
            key = (r["startTick"], r["durationTicks"])
            if figures.get(key) == "nota":
                errors.append(f"Hay un silencio de la {HAND_LABEL[hand]} en el compás {measure_of(r['startTick'])[0]} "
                              "que coincide con una nota: quitá el silencio.")
                bad = True
            elif key in figures:
                errors.append(f"Silencio repetido en la {HAND_LABEL[hand]}, compás {measure_of(r['startTick'])[0]}.")
                bad = True
            figures.setdefault(key, "silencio")
        ordered = sorted(figures)
        # Superposiciones (voces superpuestas) y huecos
        pos = 0
        for start, dur in ordered:
            m = measure_of(start)[0]
            if start < pos:
                errors.append(f"En la {HAND_LABEL[hand]} hay figuras superpuestas en el compás {m} "
                              "(voces superpuestas o duraciones de un acorde distintas). "
                              "Cada nota o silencio tiene que empezar justo cuando termina el anterior (regla 5).")
                bad = True
            elif start > pos:
                gm = measure_of(pos)[0] if pos < end else m
                errors.append(f"La {HAND_LABEL[hand]} no tiene nada entre el tick {pos} y el {start} "
                              f"(compás {gm}): falta un silencio de {start - pos} ticks (regla 3).")
                bad = True
            pos = max(pos, start + dur)
        if pos < end:
            errors.append(f"La {HAND_LABEL[hand]} termina en el tick {pos} y la partitura sigue hasta el {end}: "
                          f"falta un silencio de {end - pos} ticks (compás {measure_of(pos)[0]}) (regla 3).")
            bad = True
        # Último compás completo
        last = measures[-1]
        if end != last[2]:
            if hand == HANDS[0]:
                errors.append(f"El último compás (el {last[0]}) está incompleto: termina en el tick {end} y tendría "
                              f"que terminar en el {last[2]}. Completalo con silencios en las dos manos (regla 3).")
        # Suma por compás (solo si no hay otros problemas en la mano, para no repetir avisos)
        if not bad:
            for num, ms, me in measures:
                total = sum(min(s + d, me) - max(s, ms) for s, d in ordered if s < me and s + d > ms)
                if total != me - ms:
                    errors.append(f"En el compás {num} la {HAND_LABEL[hand]} suma {total} ticks y tendría que sumar {me - ms}.")
    return errors


def validate_file(path):
    try:
        with open(path, "rb") as f:
            raw = f.read()
    except OSError as e:
        return [f"No se pudo leer el archivo: {e.strerror}."]
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        return ["El archivo no está en UTF-8."]
    try:
        data = json.loads(text)
    except json.JSONDecodeError as e:
        return [f"No es un JSON válido (línea {e.lineno}, columna {e.colno}): {e.msg}. "
                "Sin comentarios ni comas de más al final de una lista."]
    return validate(data)


def main(argv):
    paths = [a for a in argv[1:] if not a.startswith("-")]
    if not paths:
        print(__doc__.strip())
        return 2
    failed = False
    for path in paths:
        problems = validate_file(path)
        if problems:
            failed = True
            print(f"✗ {path}: {len(problems)} problema(s)")
            for p in problems:
                print(f"  - {p}")
        else:
            print(f"✓ {path}: cumple el contrato")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
