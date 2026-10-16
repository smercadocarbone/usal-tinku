package com.tinku.identidad.web;

import com.tinku.identidad.dto.EspecialidadResponse;
import com.tinku.identidad.dto.GuardarEspecialidadesRequest;
import com.tinku.identidad.model.TipoArchivoCredencial;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.ArchivoCredencialDemasiadoGrandeException;
import com.tinku.identidad.service.ArchivoCredencialInvalidoException;
import com.tinku.identidad.service.CargaCredencialService;
import com.tinku.identidad.service.EspecialidadService;
import com.tinku.identidad.service.EspecialidadesFlag;
import com.tinku.identidad.service.SoloTutorException;
import com.tinku.identidad.service.TriajeCredencial;
import com.tinku.shared.UsuarioActual;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * ADR-M1-06 (T-ESP-01..04): especialidades del Tutor y prellenado de la credencial. Todo responde
 * 404 con {@code tinku.especialidades.habilitado} apagado, salvo {@code /config}, que le dice al
 * frontend si mostrar la UI nueva.
 */
@RestController
@RequestMapping("/api")
public class EspecialidadController {

    private final EspecialidadesFlag flag;
    private final EspecialidadService especialidades;
    private final CargaCredencialService carga;
    private final UsuarioActual usuarioActual;
    private final DataSize maxArchivo;

    public EspecialidadController(EspecialidadesFlag flag, EspecialidadService especialidades,
                                  CargaCredencialService carga, UsuarioActual usuarioActual,
                                  @Value("${spring.servlet.multipart.max-file-size}") DataSize maxArchivo) {
        this.flag = flag;
        this.especialidades = especialidades;
        this.carga = carga;
        this.usuarioActual = usuarioActual;
        this.maxArchivo = maxArchivo;
    }

    public record ConfigEspecialidades(boolean habilitado) {
    }

    @GetMapping("/especialidades/config")
    public ConfigEspecialidades config() {
        return new ConfigEspecialidades(flag.habilitado());
    }

    @GetMapping("/especialidades/materias")
    public List<String> materias() {
        flag.exigir();
        return especialidades.materias();
    }

    @GetMapping("/tutores/me/especialidades")
    public List<EspecialidadResponse> mias(Authentication authentication) {
        flag.exigir();
        Usuario u = usuarioActual.obtener(authentication);
        if (u.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException();
        }
        return especialidades.listar(u.getId());
    }

    @PutMapping("/tutores/me/especialidades")
    public List<EspecialidadResponse> guardar(@RequestBody GuardarEspecialidadesRequest request,
                                              Authentication authentication) {
        flag.exigir();
        return especialidades.guardar(usuarioActual.obtener(authentication), request);
    }

    /** Prellenado por OCR de institución, título y año: el Tutor lo confirma antes de enviar. */
    @PostMapping(value = "/tutores/credenciales/prellenar", consumes = "multipart/form-data")
    public TriajeCredencial.Prellenado prellenar(@RequestPart("archivo") MultipartFile archivo,
                                                 Authentication authentication) throws IOException {
        flag.exigir();
        if (archivo.getSize() > maxArchivo.toBytes()) {
            throw new ArchivoCredencialDemasiadoGrandeException(maxArchivo);
        }
        byte[] contenido = archivo.getBytes();
        if (TipoArchivoCredencial.detectar(contenido).isEmpty()) {
            throw new ArchivoCredencialInvalidoException();
        }
        return carga.prellenar(usuarioActual.obtener(authentication), contenido);
    }
}
