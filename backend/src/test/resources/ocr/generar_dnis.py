"""Genera DNIs SINTÉTICOS (datos inventados) con el formato de la tarjeta actual, simulados
como fotos de celular, para probar el OCR real (TesseractOcrServiceRealTest).

Nunca usar DNIs reales en el repo. Uso: python3 generar_dnis.py (requiere Pillow).
"""
import io
import math
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FUENTE = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FUENTE_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
random.seed(7)


def f(tam, bold=False, mono=False):
    return ImageFont.truetype(MONO if mono else (FUENTE_B if bold else FUENTE), tam)


def fondo(w, h):
    img = Image.new("RGB", (w, h), (226, 236, 244))
    d = ImageDraw.Draw(img)
    # Guilloché: ondas celestes finas, como el fondo de seguridad del DNI.
    for k in range(0, h, 9):
        pts = [(x, k + 6 * math.sin(x / 37.0 + k)) for x in range(0, w, 6)]
        d.line(pts, fill=(196, 214, 232), width=2)
    return img


def frente(apellido, nombre, dni, nac):
    w, h = 1700, 1070
    img = fondo(w, h)
    d = ImageDraw.Draw(img)
    d.text((60, 40), "REPÚBLICA ARGENTINA - MERCOSUR", font=f(46, True), fill=(30, 50, 90))
    d.text((60, 100), "REGISTRO NACIONAL DE LAS PERSONAS", font=f(30), fill=(30, 50, 90))
    d.rectangle((60, 190, 520, 780), fill=(170, 170, 175))  # foto
    x, y = 580, 190
    campos = [
        ("Apellido / Surname", apellido),
        ("Nombre / Name", nombre),
        ("Sexo / Sex", "F"),
        ("Nacionalidad / Nationality", "ARGENTINA"),
        ("Fecha de nacimiento / Date of birth", nac),
        ("Fecha de emisión / Date of issue", "10 ENE/ JAN 2018"),
        ("Fecha de vencimiento / Date of expiry", "10 ENE/ JAN 2033"),
    ]
    for rot, val in campos:
        d.text((x, y), rot, font=f(26), fill=(70, 80, 100))
        d.text((x, y + 32), val, font=f(44, True), fill=(15, 15, 20))
        y += 108
    d.text((60, 820), "Documento / Document", font=f(26), fill=(70, 80, 100))
    d.text((60, 852), dni, font=f(64, True), fill=(15, 15, 20))
    d.text((60, 960), "Trámite Nº / Of. ident. 00512345678 A", font=f(26), fill=(70, 80, 100))
    return img


def dorso(apellido, nombre, dni, nac_yymmdd):
    def control(campo):
        pesos, s = [7, 3, 1], 0
        for i, c in enumerate(campo):
            v = int(c) if c.isdigit() else (0 if c == "<" else ord(c) - 55)
            s += v * pesos[i % 3]
        return str(s % 10)

    doc = dni.replace(".", "").rjust(9, "<")
    l1 = ("IDARG" + doc + control(doc)).ljust(30, "<")
    l2 = (nac_yymmdd + control(nac_yymmdd) + "F3301106ARG").ljust(29, "<") + "6"
    l3 = (apellido.replace(" ", "<") + "<<" + nombre.replace(" ", "<")).ljust(30, "<")
    w, h = 1700, 1070
    img = fondo(w, h)
    d = ImageDraw.Draw(img)
    d.text((60, 60), "DOMICILIO: AV. SIEMPRE VIVA 742 - CABA", font=f(34), fill=(15, 15, 20))
    d.rectangle((0, 700, w, h), fill=(240, 244, 248))
    for i, l in enumerate([l1, l2, l3]):
        d.text((70, 730 + i * 105), l, font=f(62, mono=True), fill=(10, 10, 10))
    return img


def foto_de_celular(tarjeta, angulo=4.0, orientacion_exif=None, borroso=1.0):
    """Tarjeta sobre una mesa, un poco girada, con luz despareja, ruido y JPEG de celular."""
    W, H = 4032, 3024
    mesa = Image.new("RGB", (W, H), (120, 96, 72))
    luz = Image.linear_gradient("L").resize((W, H)).point(lambda v: 60 + v // 3)
    mesa = Image.composite(mesa, Image.new("RGB", (W, H), (60, 48, 36)), luz)
    t = tarjeta.resize((3000, int(3000 * tarjeta.height / tarjeta.width)))
    t = t.rotate(angulo, expand=True, fillcolor=(120, 96, 72))
    mesa.paste(t, ((W - t.width) // 2, (H - t.height) // 2))
    mesa = mesa.filter(ImageFilter.GaussianBlur(borroso))
    ruido = Image.effect_noise((W, H), 12).convert("RGB")
    mesa = Image.blend(mesa, ruido, 0.06)
    exif = None
    if orientacion_exif == 6:
        # El sensor guarda la foto acostada y el EXIF dice "rotar 90° al mostrar".
        mesa = mesa.rotate(90, expand=True)
        exif = Image.Exif()
        exif[0x0112] = 6
    out = io.BytesIO()
    mesa.save(out, "JPEG", quality=82, **({"exif": exif} if exif else {}))
    return out.getvalue()


if __name__ == "__main__":
    casos = {
        "frente_foto.jpg": foto_de_celular(frente("GONZALEZ", "MARIA SOL", "34.567.890", "15 MAY/ MAY 1990")),
        "frente_exif6.jpg": foto_de_celular(frente("PEREYRA", "JUAN IGNACIO", "40.123.456", "03 AGO/ AUG 1997"),
                                           angulo=-3.0, orientacion_exif=6),
        "dorso_foto.jpg": foto_de_celular(dorso("GONZALEZ", "MARIA SOL", "34567890", "900515"), angulo=2.0),
    }
    for nombre, datos in casos.items():
        with open(nombre, "wb") as fh:
            fh.write(datos)
        print(nombre, len(datos) // 1024, "KB")
