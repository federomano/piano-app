"""Pruebas del validador. Correr con: python3 -I -m unittest discover -s tools -v"""
import copy
import glob
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import validar_partitura as v  # noqa: E402

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BASE = {
    "formatVersion": 1, "title": "Prueba", "level": "principiante", "goal": "Probar",
    "timeSignatures": [{"startTick": 0, "numerator": 4, "denominator": 4}],
    "keySignatures": [{"startTick": 0, "fifths": 0}],
    "tempos": [{"startTick": 0, "bpm": 80.0}],
    "notes": [
        {"hand": "right", "pitch": "C4", "startTick": 0, "durationTicks": 960},
        {"hand": "right", "pitch": "D4", "startTick": 960, "durationTicks": 960},
        {"hand": "left", "pitch": "C3", "startTick": 0, "durationTicks": 1920},
    ],
    "rests": [],
}


def con(cambio):
    d = copy.deepcopy(BASE)
    cambio(d)
    return d


class Validador(unittest.TestCase):
    def assertProblema(self, data, texto):
        errores = v.validate(data)
        self.assertTrue(any(texto in e for e in errores), f"Se esperaba «{texto}» en {errores}")

    def test_base_esta_bien(self):
        self.assertEqual(v.validate(BASE), [])

    def test_ejemplos_de_referencia_cumplen(self):
        archivos = glob.glob(os.path.join(RAIZ, "docs", "ejemplos", "*.json"))
        self.assertGreaterEqual(len(archivos), 4)
        for a in archivos:
            self.assertEqual(v.validate_file(a), [], a)

    def test_mano_sin_silencio(self):
        errores = v.validate(con(lambda d: d["notes"].pop()))
        self.assertTrue(any("mano izquierda" in e and "falta un silencio" in e for e in errores), errores)

    def test_silencio_completa_el_compas(self):
        d = con(lambda d: (d["notes"].pop(), d["rests"].append({"hand": "left", "startTick": 0, "durationTicks": 1920})))
        self.assertEqual(v.validate(d), [])

    def test_compas_que_no_suma(self):
        self.assertProblema(con(lambda d: d["notes"][1].update(durationTicks=480)), "falta un silencio")

    def test_tresillo(self):
        self.assertProblema(con(lambda d: d["notes"][0].update(durationTicks=320)), "no es una figura que la app sepa dibujar")

    def test_fuera_de_rango(self):
        self.assertProblema(con(lambda d: d["notes"][0].update(pitch="C9")), "fuera del rango del piano")

    def test_altura_invalida(self):
        self.assertProblema(con(lambda d: d["notes"][0].update(pitch="H4")), "altura inválida")

    def test_cruza_barra(self):
        def c(d):
            d["notes"][1].update(startTick=1440, durationTicks=960)
            d["notes"][0].update(durationTicks=1440)
        self.assertProblema(con(c), "cruza la barra de compás")

    def test_ligadura_sin_segunda_nota(self):
        self.assertProblema(con(lambda d: d["notes"][0].update(tiedToNext=True)), "está ligada")

    def test_voces_superpuestas(self):
        def c(d):
            d["notes"].append({"hand": "right", "pitch": "E4", "startTick": 0, "durationTicks": 1920})
        self.assertProblema(con(c), "superpuestas")

    def test_clave_desconocida(self):
        self.assertProblema(con(lambda d: d["notes"][0].update(dedo=1)), "no existe en el modelo")

    def test_falta_titulo_nivel_y_meta(self):
        def c(d):
            del d["title"], d["level"], d["goal"]
        e = v.validate(con(c))
        self.assertTrue(any("título" in x for x in e) and any("level" in x for x in e) and any("goal" in x for x in e))

    def test_digitacion_a_medias(self):
        self.assertProblema(con(lambda d: d["notes"][0].update(finger=1)), "Digitación a medias")

    def test_ultimo_compas_incompleto(self):
        def c(d):
            d["notes"][1].update(durationTicks=480)
            d["notes"][2].update(durationTicks=1440)
        self.assertProblema(con(c), "incompleto")

    def test_bpm_en_otra_figura(self):
        self.assertProblema(con(lambda d: d["tempos"][0].update(bpm=500)), "negras por minuto")

    def test_cambio_de_compas_a_mitad(self):
        self.assertProblema(con(lambda d: d["timeSignatures"].append({"startTick": 960, "numerator": 3, "denominator": 4})),
                            "no cae al inicio de un compás")

    # --- Grupos irregulares ---
    def tresillos(self, cantidad=3, tuplet="3:2", dur=160):
        """Compás con `cantidad` figuras de tresillo en la derecha y el resto lleno con una figura común."""
        def c(d):
            d["notes"] = [{"hand": "left", "pitch": "C3", "startTick": 0, "durationTicks": 1920}]
            for i in range(cantidad):
                n = {"hand": "right", "pitch": "C4", "startTick": i * dur, "durationTicks": dur}
                if tuplet:
                    n["tuplet"] = tuplet
                d["notes"].append(n)
            resto = 1920 - cantidad * dur
            if resto:
                d["rests"] = [{"hand": "right", "startTick": cantidad * dur, "durationTicks": resto}]
        return con(c)

    def test_tresillo_con_tuplet_esta_bien(self):
        d = self.tresillos(3)
        d["rests"] = [{"hand": "right", "startTick": 480, "durationTicks": 960},
                      {"hand": "right", "startTick": 1440, "durationTicks": 480}]
        self.assertEqual(v.validate(d), [])

    def test_tresillo_sin_tuplet(self):
        self.assertProblema(self.tresillos(3, tuplet=None), "agregá \"tuplet\"")

    def test_tuplet_mal_escrito(self):
        self.assertProblema(self.tresillos(3, tuplet="tres"), "tiene que escribirse como \"3:2\"")

    def test_tuplet_con_duracion_que_no_da_figura(self):
        self.assertProblema(self.tresillos(3, dur=200), "no puede durar 200 ticks")

    def test_grupo_incompleto(self):
        d = self.tresillos(2)
        d["rests"] = [{"hand": "right", "startTick": 320, "durationTicks": 1600}]
        self.assertProblema(d, "incompleto")

    def test_cuatro_en_el_tiempo_de_tres(self):
        d = {**BASE, "timeSignatures": [{"startTick": 0, "numerator": 3, "denominator": 4}],
             "notes": [{"hand": "right", "pitch": "C4", "startTick": i * 360, "durationTicks": 360, "tuplet": "4:3"} for i in range(4)]
             + [{"hand": "left", "pitch": "C3", "startTick": 0, "durationTicks": 1440}], "rests": []}
        self.assertEqual(v.validate(d), [])

    # --- Voces ---
    def dos_voces(self):
        d = copy.deepcopy(BASE)
        d["notes"] = [
            {"hand": "right", "pitch": "E5", "startTick": 0, "durationTicks": 1920},
            {"hand": "right", "pitch": "C4", "startTick": 0, "durationTicks": 960, "voice": 2},
            {"hand": "right", "pitch": "E4", "startTick": 960, "durationTicks": 960, "voice": 2},
            {"hand": "left", "pitch": "C3", "startTick": 0, "durationTicks": 1920},
        ]
        return d

    def test_dos_voces_en_una_mano_estan_bien(self):
        self.assertEqual(v.validate(self.dos_voces()), [])

    def test_voces_sin_voice_se_marcan_como_superpuestas(self):
        d = self.dos_voces()
        for n in d["notes"]:
            n.pop("voice", None)
        self.assertProblema(d, "superpuestas")

    def test_una_voz_secundaria_tiene_que_llenar_sus_compases(self):
        d = self.dos_voces()
        d["notes"][2]["durationTicks"] = 480
        self.assertProblema(d, "voz 2")

    def test_voz_secundaria_no_hace_falta_en_todos_los_compases(self):
        d = self.dos_voces()
        d["notes"] += [{"hand": "right", "pitch": "G4", "startTick": 1920, "durationTicks": 1920},
                       {"hand": "left", "pitch": "C3", "startTick": 1920, "durationTicks": 1920}]
        self.assertEqual(v.validate(d), [])

    def test_voz_fuera_de_rango(self):
        d = self.dos_voces()
        d["notes"][1]["voice"] = 5
        self.assertProblema(d, "\"voice\" tiene que ser un entero de 1 a 4")

    def test_acorde_con_notas_de_distinta_duracion_en_la_misma_voz(self):
        d = self.dos_voces()
        d["notes"][1].pop("voice")
        self.assertProblema(d, "superpuestas")

    def test_la_misma_tecla_dos_veces_a_la_vez(self):
        d = self.dos_voces()
        d["notes"].append({"hand": "right", "pitch": "E5", "startTick": 960, "durationTicks": 960, "voice": 3})
        self.assertProblema(d, "se pisan")

    def test_anacrusa_bien(self):
        # Compás 0 de una negra y después un compás entero.
        d = con(lambda d: d.update(
            pickupTicks=480,
            notes=[{"hand": "right", "pitch": "G4", "startTick": 0, "durationTicks": 480},
                   {"hand": "right", "pitch": "C5", "startTick": 480, "durationTicks": 1920},
                   {"hand": "left", "pitch": "C3", "startTick": 480, "durationTicks": 1920}],
            rests=[{"hand": "left", "startTick": 0, "durationTicks": 480}]))
        self.assertEqual(v.validate(d), [])

    def test_anacrusa_sin_llenar_la_mano_izquierda(self):
        d = con(lambda d: d.update(
            pickupTicks=480,
            notes=[{"hand": "right", "pitch": "G4", "startTick": 0, "durationTicks": 480},
                   {"hand": "right", "pitch": "C5", "startTick": 480, "durationTicks": 1920},
                   {"hand": "left", "pitch": "C3", "startTick": 480, "durationTicks": 1920}]))
        self.assertProblema(d, "compás 0")

    def test_anacrusa_no_puede_cruzar_su_barra(self):
        d = con(lambda d: d.update(
            pickupTicks=480,
            notes=[{"hand": "right", "pitch": "G4", "startTick": 0, "durationTicks": 960},
                   {"hand": "left", "pitch": "C3", "startTick": 0, "durationTicks": 960}]))
        self.assertProblema(d, "cruza la barra")

    def test_anacrusa_demasiado_larga(self):
        d = con(lambda d: d.update(pickupTicks=1920))
        self.assertProblema(d, "pickupTicks")

    def test_anacrusa_negativa(self):
        d = con(lambda d: d.update(pickupTicks=-5))
        self.assertProblema(d, "pickupTicks")

    def test_fusas(self):
        def fusas(d):
            # Un compás de 4/4 en la derecha: ocho fusas (480), una negra, una corchea con puntillo y una fusa con puntillo + dos más.
            ns = [{"hand": "right", "pitch": "C5", "startTick": i * 60, "durationTicks": 60} for i in range(8)]
            ns += [{"hand": "right", "pitch": "D5", "startTick": 480, "durationTicks": 960},
                   {"hand": "right", "pitch": "E5", "startTick": 1440, "durationTicks": 90},
                   {"hand": "right", "pitch": "F5", "startTick": 1530, "durationTicks": 30}]
            d["notes"] = ns + [n for n in d["notes"] if n["hand"] == "left"]
        # 30 ticks no es una figura: el validador lo dice; sin esa nota, las fusas están bien.
        self.assertProblema(con(fusas), "30")
        def bien(d):
            fusas(d)
            d["notes"] = [n for n in d["notes"] if n["durationTicks"] != 30 and n["durationTicks"] != 90]
            d["notes"].append({"hand": "right", "pitch": "E5", "startTick": 1440, "durationTicks": 480})
        errores = [e for e in v.validate(con(bien)) if "duración" in e or "figura" in e]
        self.assertEqual(errores, [])

    def test_adorno_bien(self):
        d = con(lambda d: d["notes"].append(
            {"hand": "right", "pitch": "B3", "startTick": 960, "durationTicks": 60, "grace": True}))
        self.assertEqual(v.validate(d), [])

    def test_adorno_sin_nota_principal(self):
        d = con(lambda d: d["notes"].append(
            {"hand": "right", "pitch": "B3", "startTick": 480, "durationTicks": 60, "grace": True}))
        self.assertProblema(d, "no tiene una nota principal")

    def test_adorno_demasiado_largo(self):
        d = con(lambda d: d["notes"].append(
            {"hand": "right", "pitch": "B3", "startTick": 960, "durationTicks": 480, "grace": True}))
        self.assertProblema(d, "como mucho")

    def test_adorno_ligado_o_con_tuplet(self):
        d = con(lambda d: d["notes"].append(
            {"hand": "right", "pitch": "B3", "startTick": 960, "durationTicks": 60, "grace": True, "tiedToNext": True}))
        self.assertProblema(d, "ligado")
        d = con(lambda d: d["notes"].append(
            {"hand": "right", "pitch": "B3", "startTick": 960, "durationTicks": 60, "grace": True, "tuplet": "3:2"}))
        self.assertProblema(d, "tuplet")

    def test_adorno_no_tiene_que_ser_true_o_false(self):
        d = con(lambda d: d["notes"].append(
            {"hand": "right", "pitch": "B3", "startTick": 960, "durationTicks": 60, "grace": "si"}))
        self.assertProblema(d, "true o false")

    def test_dinamicas_bien(self):
        def cambio(d):
            d["dynamics"] = [{"startTick": 0, "level": "p"}, {"startTick": 960, "level": "f"}]
            d["hairpins"] = [{"startTick": 0, "endTick": 960, "kind": "crescendo"}]
        self.assertEqual(v.validate(con(cambio)), [])

    def test_dinamica_con_nivel_inventado(self):
        d = con(lambda d: d.__setitem__("dynamics", [{"startTick": 0, "level": "forte"}]))
        self.assertProblema(d, "\"level\" tiene que ser uno de")

    def test_dinamicas_desordenadas_o_repetidas(self):
        d = con(lambda d: d.__setitem__("dynamics", [{"startTick": 960, "level": "p"}, {"startTick": 0, "level": "f"}]))
        self.assertProblema(d, "ordenadas")

    def test_dinamica_despues_del_final(self):
        d = con(lambda d: d.__setitem__("dynamics", [{"startTick": 9600, "level": "f"}]))
        self.assertProblema(d, "después del final")

    def test_regulador_mal_armado(self):
        d = con(lambda d: d.__setitem__("hairpins", [{"startTick": 960, "endTick": 960, "kind": "crescendo"}]))
        self.assertProblema(d, "mayor que")
        d = con(lambda d: d.__setitem__("hairpins", [{"startTick": 0, "endTick": 960, "kind": "sube"}]))
        self.assertProblema(d, "crescendo")

    def test_reguladores_que_se_pisan_y_dinamica_en_el_medio(self):
        d = con(lambda d: d.__setitem__("hairpins", [
            {"startTick": 0, "endTick": 1440, "kind": "crescendo"}, {"startTick": 960, "endTick": 1920, "kind": "diminuendo"}]))
        self.assertProblema(d, "se pisa")
        def cambio(d):
            d["hairpins"] = [{"startTick": 0, "endTick": 1920, "kind": "crescendo"}]
            d["dynamics"] = [{"startTick": 960, "level": "f"}]
        self.assertProblema(con(cambio), "en el medio de un regulador")

    def test_clave_de_dinamica_desconocida(self):
        d = con(lambda d: d.__setitem__("dynamics", [{"startTick": 0, "level": "p", "color": "rojo"}]))
        self.assertProblema(d, "no existe en el modelo")

    def test_articulaciones_bien(self):
        def cambio(d):
            d["notes"][0]["staccato"] = True
            d["notes"][1]["accent"] = True
            d["notes"][1]["staccato"] = True
        self.assertEqual(v.validate(con(cambio)), [])

    def test_articulacion_no_booleana(self):
        d = con(lambda d: d["notes"][0].__setitem__("accent", "si"))
        self.assertProblema(d, "true o false")

    def test_staccato_en_nota_ligada_o_adorno(self):
        def ligada(d):
            d["notes"][0].update({"durationTicks": 960, "tiedToNext": True, "staccato": True})
            d["notes"][1].update({"pitch": "C4"})
        self.assertProblema(con(ligada), "no puede llevar \"staccato\"")
        d = con(lambda d: d["notes"].append(
            {"hand": "right", "pitch": "B3", "startTick": 960, "durationTicks": 60, "grace": True, "accent": True}))
        self.assertProblema(d, "no puede llevar \"accent\"")

    def test_acorde_con_articulaciones_distintas(self):
        def cambio(d):
            d["notes"].append({"hand": "left", "pitch": "G3", "startTick": 0, "durationTicks": 1920, "staccato": True})
        self.assertProblema(con(cambio), "el mismo \"staccato\"")

    def test_json_roto(self):
        import tempfile
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
            f.write('{"title": "x",}')
        try:
            self.assertTrue(any("No es un JSON válido" in e for e in v.validate_file(f.name)))
        finally:
            os.unlink(f.name)


if __name__ == "__main__":
    unittest.main()
