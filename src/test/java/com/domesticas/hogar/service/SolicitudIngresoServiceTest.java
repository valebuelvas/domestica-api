package com.domesticas.hogar.service;

import com.domesticas.exception.BadRequestException;
import com.domesticas.hogar.model.Hogar;
import com.domesticas.hogar.model.MiembroHogar;
import com.domesticas.hogar.model.Rol;
import com.domesticas.hogar.model.SolicitudIngreso;
import com.domesticas.hogar.repository.HogarRepository;
import com.domesticas.hogar.repository.MiembroHogarRepository;
import com.domesticas.hogar.repository.RolRepository;
import com.domesticas.hogar.repository.SolicitudIngresoRepository;
import com.domesticas.usuario.model.Usuario;
import com.domesticas.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SolicitudIngresoServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private HogarRepository hogarRepository;
    @Mock private MiembroHogarRepository miembroHogarRepository;
    @Mock private SolicitudIngresoRepository solicitudIngresoRepository;
    @Mock private RolRepository rolRepository;

    @InjectMocks
    private SolicitudIngresoService solicitudIngresoService;

    private Usuario usuarioSolicitante;
    private Usuario usuarioAdmin;
    private Hogar hogar;
    private Rol rolHijo;
    private MiembroHogar miembroAdmin;
    private MiembroHogar miembroRegular;
    private SolicitudIngreso solicitudPendiente;

    @BeforeEach
    void setUp() {
        usuarioSolicitante = Usuario.builder()
                .id(3L).nombre("Solicitante")
                .email("solicitante@test.com").password("hash").build();

        usuarioAdmin = Usuario.builder()
                .id(1L).nombre("Admin")
                .email("admin@test.com").password("hash").build();

        hogar = Hogar.builder()
                .id(10L).nombre("Hogar Test")
                .codigoAcceso("ABC123").build();

        rolHijo = Rol.builder().id(3L).nombre("Hijo").build();

        Rol rolPadre = Rol.builder().id(1L).nombre("Padre").build();

        miembroAdmin = MiembroHogar.builder()
                .id(1L).usuario(usuarioAdmin).hogar(hogar)
                .rol(rolPadre).esAdministrador(true).build();

        miembroRegular = MiembroHogar.builder()
                .id(2L).usuario(usuarioSolicitante).hogar(hogar)
                .rol(rolHijo).esAdministrador(false).build();

        solicitudPendiente = SolicitudIngreso.builder()
                .id(1L).usuario(usuarioSolicitante)
                .hogar(hogar).estado("PENDIENTE").build();
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP015 — Solicitud de ingreso con código válido
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP015 - Solicitud con código válido se registra como PENDIENTE")
    void solicitarIngreso_ConCodigoValido_DebeRegistrarSolicitudPendiente() {

        when(usuarioRepository.findByEmail("solicitante@test.com"))
                .thenReturn(Optional.of(usuarioSolicitante));
        when(hogarRepository.findByCodigoAcceso("ABC123"))
                .thenReturn(Optional.of(hogar));
        // No es miembro aún
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "solicitante@test.com"))
                .thenReturn(Optional.empty());
        // No tiene solicitud pendiente previa
        when(solicitudIngresoRepository.existsByUsuarioAndHogarAndEstado(
                usuarioSolicitante, hogar, "PENDIENTE"))
                .thenReturn(false);

        solicitudIngresoService.solicitarIngreso("solicitante@test.com", "ABC123");

        // La solicitud debe guardarse con estado PENDIENTE
        verify(solicitudIngresoRepository, times(1)).save(argThat(s ->
                "PENDIENTE".equals(s.getEstado())
                        && s.getUsuario().equals(usuarioSolicitante)
                        && s.getHogar().equals(hogar)
        ));
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP016 — Código de acceso inválido
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP016 - Código inexistente lanza BadRequestException")
    void solicitarIngreso_ConCodigoInvalido_DebeLanzarExcepcion() {

        when(usuarioRepository.findByEmail("solicitante@test.com"))
                .thenReturn(Optional.of(usuarioSolicitante));
        // El código no corresponde a ningún hogar
        when(hogarRepository.findByCodigoAcceso("INVALIDO"))
                .thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> solicitudIngresoService.solicitarIngreso(
                        "solicitante@test.com", "INVALIDO")
        );

        assertEquals("Código inválido", ex.getMessage());
        verify(solicitudIngresoRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP017 — Solicitud duplicada bloqueada
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP017 - Solicitud duplicada lanza BadRequestException")
    void solicitarIngreso_ConSolicitudYaPendiente_DebeLanzarExcepcion() {

        when(usuarioRepository.findByEmail("solicitante@test.com"))
                .thenReturn(Optional.of(usuarioSolicitante));
        when(hogarRepository.findByCodigoAcceso("ABC123"))
                .thenReturn(Optional.of(hogar));
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "solicitante@test.com"))
                .thenReturn(Optional.empty());
        // Ya existe una solicitud pendiente para este grupo
        when(solicitudIngresoRepository.existsByUsuarioAndHogarAndEstado(
                usuarioSolicitante, hogar, "PENDIENTE"))
                .thenReturn(true);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> solicitudIngresoService.solicitarIngreso(
                        "solicitante@test.com", "ABC123")
        );

        assertEquals("Ya tienes una solicitud pendiente", ex.getMessage());
        verify(solicitudIngresoRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP018 — Admin acepta solicitud: el usuario pasa a ser miembro activo
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP018 - Admin acepta solicitud y el usuario es agregado como miembro")
    void responderSolicitud_AdminAcepta_DebeAgregarMiembroYActualizarEstado() {

        when(solicitudIngresoRepository.findById(1L))
                .thenReturn(Optional.of(solicitudPendiente));
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "admin@test.com"))
                .thenReturn(Optional.of(miembroAdmin));
        when(rolRepository.findByNombre("Hijo"))
                .thenReturn(Optional.of(rolHijo));

        solicitudIngresoService.responderSolicitud(1L, "admin@test.com", "ACEPTAR", "Hijo");

        // El nuevo miembro debe guardarse con esAdministrador=false
        verify(miembroHogarRepository, times(1)).save(argThat(m ->
                !Boolean.TRUE.equals(m.getEsAdministrador())
                        && m.getUsuario().equals(usuarioSolicitante)
        ));

        // La solicitud debe actualizarse a ACEPTADA
        verify(solicitudIngresoRepository, times(1)).save(argThat(s ->
                "ACEPTADA".equals(s.getEstado())
        ));
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP019 — Admin rechaza solicitud: el usuario NO es agregado al grupo
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP019 - Admin rechaza solicitud y el estado cambia a RECHAZADA")
    void responderSolicitud_AdminRechaza_NoDebeAgregarMiembro() {

        when(solicitudIngresoRepository.findById(1L))
                .thenReturn(Optional.of(solicitudPendiente));
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "admin@test.com"))
                .thenReturn(Optional.of(miembroAdmin));

        solicitudIngresoService.responderSolicitud(1L, "admin@test.com", "RECHAZAR", null);

        // No debe agregarse ningún miembro nuevo
        verify(miembroHogarRepository, never()).save(any());

        // La solicitud debe actualizarse a RECHAZADA
        verify(solicitudIngresoRepository, times(1)).save(argThat(s ->
                "RECHAZADA".equals(s.getEstado())
        ));
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP020 — Miembro regular no puede gestionar solicitudes
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP020 - Miembro regular intenta gestionar solicitudes y recibe error")
    void responderSolicitud_MiembroRegular_DebeLanzarExcepcion() {

        when(solicitudIngresoRepository.findById(1L))
                .thenReturn(Optional.of(solicitudPendiente));
        // El que responde es un miembro regular, no admin
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "miembro@test.com"))
                .thenReturn(Optional.of(miembroRegular));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> solicitudIngresoService.responderSolicitud(
                        1L, "miembro@test.com", "ACEPTAR", "Hijo")
        );

        assertEquals("Solo el administrador puede gestionar solicitudes", ex.getMessage());

        // Nada debe guardarse
        verify(miembroHogarRepository, never()).save(any());
        verify(solicitudIngresoRepository, never()).save(any());
    }
}