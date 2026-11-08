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
