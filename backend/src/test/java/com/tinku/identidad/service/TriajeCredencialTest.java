package com.tinku.identidad.service;

import com.tinku.identidad.ocr.TextoDocumento;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR-M1-06 (E2/E4): reglas puras del triaje automático de una credencial. */
class TriajeCredencialTest {

    @Test
    void enlace_soloHttpsYDominiosOficiales() {
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://registrograduados.siu.edu.ar/consulta")).isEmpty();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://mi.argentina.gob.ar/titulos/abc")).isEmpty();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://www.argentina.gob.ar/x")).isEmpty();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://argentina.gob.ar/x")).isEmpty();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://guarani.unc.edu.ar/validar?c=1")).isEmpty();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("HTTPS://Guarani.UNC.edu.AR/v")).isEmpty();

        assertThat(TriajeCredencial.motivoEnlaceInvalido("http://registrograduados.siu.edu.ar/x")).isPresent();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://registrograduados.siu.edu.ar.evil.com/x")).isPresent();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://evil.com/argentina.gob.ar")).isPresent();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://fakeargentina.gob.ar/x")).isPresent();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://edu.ar/x")).isPresent();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://user@guarani.unc.edu.ar/x")).isPresent();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("https://guarani.unc.edu.ar:8443/x")).isPresent();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("javascript:alert(1)")).isPresent();
        assertThat(TriajeCredencial.motivoEnlaceInvalido("no es un enlace")).isPresent();
    }

    @Test
    void texto_sinTextoDniUOtraCosaSeRechazan_tituloONoProcesableNo() {
        assertThat(TriajeCredencial.motivoRechazoPorTexto(TextoDocumento.sinTexto())).isPresent();
        assertThat(TriajeCredencial.motivoRechazoPorTexto(TextoDocumento.leido(
                "DOCUMENTO NACIONAL DE IDENTIDAD\nIDARG30111222<5<<<<<"))).get().asString().contains("DNI");
        assertThat(TriajeCredencial.motivoRechazoPorTexto(TextoDocumento.leido("Captura de un chat de WhatsApp")))
                .isPresent();
        assertThat(TriajeCredencial.motivoRechazoPorTexto(TextoDocumento.leido(
                "Instituto Superior del Profesorado N° 3\nTítulo de Profesor de Historia"))).isEmpty();
        assertThat(TriajeCredencial.motivoRechazoPorTexto(TextoDocumento.leido(
                "SIU GUARANI - Certificado de alumno regular\nCódigo de validación: X1"))).isEmpty();
        assertThat(TriajeCredencial.motivoRechazoPorTexto(TextoDocumento.noProcesable())).isEmpty();
    }

    @Test
    void nombre_toleraErroresDeOcrYAcentos() {
        TextoDocumento t = TextoDocumento.leido("Se otorga a VALERIA GOMEZ el titulo de Licenciada");
        assertThat(TriajeCredencial.nombreCoincide(t, "Valeria", "Gómez")).isTrue();
        assertThat(TriajeCredencial.nombreCoincide(t, "Valeria Inés", "Gómez")).isTrue();
        assertThat(TriajeCredencial.nombreCoincide(
                TextoDocumento.leido("Se otorga a VALER1A G0MEZ el titulo"), "Valeria", "Gómez")).isTrue();
        assertThat(TriajeCredencial.nombreCoincide(t, "Martín", "Rodríguez")).isFalse();
        assertThat(TriajeCredencial.nombreCoincide(t, "Valeria", "Fernández")).isFalse();
        assertThat(TriajeCredencial.nombreCoincide(TextoDocumento.noProcesable(), "Valeria", "Gómez")).isNull();
    }

    @Test
    void prellenado_institucionTituloYAnio() {
        var p = TriajeCredencial.prellenar(TextoDocumento.leido("""
                República Argentina
                UNIVERSIDAD DE BUENOS AIRES
                Facultad de Ciencias Exactas
                otorga a Juan Pérez el título de
                Licenciado en Ciencias Matemáticas
                Buenos Aires, 3 de julio de 2009 - Resolución 1234/2010
                """));
        assertThat(p.institucion()).isEqualTo("UNIVERSIDAD DE BUENOS AIRES");
        assertThat(p.denominacion()).isEqualTo("Licenciado en Ciencias Matemáticas");
        assertThat(p.anioEgreso()).isEqualTo(2009);

        var vacio = TriajeCredencial.prellenar(TextoDocumento.noProcesable());
        assertThat(vacio.institucion()).isNull();
        assertThat(vacio.anioEgreso()).isNull();
    }
}
