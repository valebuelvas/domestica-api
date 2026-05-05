package com.domesticas.hogar.service;

import com.domesticas.exception.BadRequestException;
import com.domesticas.hogar.dto.request.ActualizarHogarRequest;
import com.domesticas.hogar.dto.request.CrearHogarRequest;
import com.domesticas.hogar.dto.response.DetalleHogarResponse;
import com.domesticas.hogar.dto.response.HogarResponse;
import com.domesticas.hogar.model.Hogar;
import com.domesticas.hogar.model.MiembroHogar;
import com.domesticas.hogar.model.Rol;
import com.domesticas.hogar.repository.HogarRepository;
import com.domesticas.hogar.repository.MiembroHogarRepository;
import com.domesticas.hogar.repository.RolRepository;
import com.domesticas.usuario.model.Usuario;
import com.domesticas.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HogarServiceTest {

    @Mock private HogarRepository hogarRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private MiembroHogarRepository miembroHogarRepository;
    @Mock private RolRepository rolRepository;

    @InjectMocks
    private HogarService hogarService;

    // ── Objetos reutilizables ────────────────────────────────────────────────
    private Usuario usuarioAdmin;
    private Usuario usuarioMiembro;
    private Rol rolPadre;
    private Hogar hogar;
    private MiembroHogar miembroAdmin;
    private MiembroHogar miembroRegular;

    @BeforeEach
    void setUp() {
        usuarioAdmin = Usuario.builder()
                .id(1L).nombre("Admin Test")
                .email("admin@test.com").password("hash").build();

        usuarioMiembro = Usuario.builder()
                .id(2L).nombre("Miembro Test")
                .email("miembro@test.com").password("hash").build();

        rolPadre = Rol.builder().id(1L).nombre("Padre").build();

        hogar = Hogar.builder()
                .id(10L).nombre("Hogar Test")
                .codigoAcceso("ABC123").descripcion("Descripción test").build();

        miembroAdmin = MiembroHogar.builder()
                .id(1L).usuario(usuarioAdmin).hogar(hogar)
                .rol(rolPadre).esAdministrador(true).build();

        miembroRegular = MiembroHogar.builder()
                .id(2L).usuario(usuarioMiembro).hogar(hogar)
                .rol(rolPadre).esAdministrador(false).build();
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP011 — Creación exitosa de grupo
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP011 - Crear hogar exitosamente asigna admin y genera código")
    void crearHogar_ConDatosValidos_DebeCrearHogarYAsignarAdmin() {

        CrearHogarRequest request = new CrearHogarRequest();
        request.setNombre("Hogar Test");

        // El código generado no existe previamente
        when(hogarRepository.existsByCodigoAcceso(anyString())).thenReturn(false);
        when(usuarioRepository.findByEmail("admin@test.com"))
                .thenReturn(Optional.of(usuarioAdmin));
        when(rolRepository.findByNombre("Padre"))
                .thenReturn(Optional.of(rolPadre));
        when(hogarRepository.save(any(Hogar.class))).thenReturn(hogar);
        when(miembroHogarRepository.save(any(MiembroHogar.class)))
                .thenReturn(miembroAdmin);

        HogarResponse response = hogarService.crearHogar("admin@test.com", request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Hogar Test", response.getNombre());
        assertEquals("Grupo creado correctamente", response.getMensaje());

        // Verifica que se guardó el hogar Y el miembro administrador
        verify(hogarRepository, times(1)).save(any(Hogar.class));
        verify(miembroHogarRepository, times(1)).save(argThat(m ->
                Boolean.TRUE.equals(m.getEsAdministrador())
        ));
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP012 — Crear grupo sin nombre lanza excepción
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP012a - Crear hogar con nombre vacío lanza BadRequestException")
    void crearHogar_ConNombreVacio_DebeLanzarExcepcion() {

        CrearHogarRequest request = new CrearHogarRequest();
        request.setNombre("");

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> hogarService.crearHogar("admin@test.com", request)
        );

        assertEquals("El nombre del grupo es obligatorio", ex.getMessage());
        verify(hogarRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP012b - Crear hogar con nombre null lanza BadRequestException")
    void crearHogar_ConNombreNull_DebeLanzarExcepcion() {

        CrearHogarRequest request = new CrearHogarRequest();
        request.setNombre(null);

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> hogarService.crearHogar("admin@test.com", request)
        );

        assertEquals("El nombre del grupo es obligatorio", ex.getMessage());
        verify(hogarRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP013 — Miembro ve nombre, descripción y lista de miembros (sin código)
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP013 - Miembro regular obtiene detalle sin ver el código de acceso")
    void obtenerDetalleHogar_MiembroRegular_NoDebeVerCodigoAcceso() {

        when(hogarRepository.findById(10L)).thenReturn(Optional.of(hogar));
        // Lista tiene solo al miembro regular (no admin)
        when(miembroHogarRepository.findByHogarId(10L))
                .thenReturn(List.of(miembroRegular));

        DetalleHogarResponse response =
                hogarService.obtenerDetalleHogar(10L, "miembro@test.com");

        assertNotNull(response);
        assertEquals("Hogar Test", response.getNombre());
        assertEquals(1, response.getMiembros().size());

        // El código de acceso debe ser null para miembros regulares
        assertNull(response.getCodigoAcceso());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP014 — Admin ve el código de acceso en el detalle del grupo
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP014 - Administrador obtiene detalle con código de acceso visible")
    void obtenerDetalleHogar_Administrador_DebeVerCodigoAcceso() {

        when(hogarRepository.findById(10L)).thenReturn(Optional.of(hogar));
        when(miembroHogarRepository.findByHogarId(10L))
                .thenReturn(List.of(miembroAdmin));

        DetalleHogarResponse response =
                hogarService.obtenerDetalleHogar(10L, "admin@test.com");

        assertNotNull(response);
        // El administrador sí debe ver el código
        assertEquals("ABC123", response.getCodigoAcceso());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP024 — Admin edita nombre y descripción del grupo exitosamente
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP024 - Admin actualiza nombre y descripción del grupo correctamente")
    void actualizarHogar_ComoAdmin_DebeActualizarInformacion() {

        ActualizarHogarRequest request = new ActualizarHogarRequest();
        request.setNombre("Hogar Actualizado");
        request.setDescripcion("Nueva descripción");

        Hogar hogarActualizado = Hogar.builder()
                .id(10L).nombre("Hogar Actualizado")
                .descripcion("Nueva descripción").codigoAcceso("ABC123").build();

        when(hogarRepository.findById(10L)).thenReturn(Optional.of(hogar));
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "admin@test.com"))
                .thenReturn(Optional.of(miembroAdmin));
        when(hogarRepository.save(any(Hogar.class))).thenReturn(hogarActualizado);

        HogarResponse response =
                hogarService.actualizarHogar(10L, "admin@test.com", request);

        assertNotNull(response);
        assertEquals("Hogar Actualizado", response.getNombre());
        assertEquals("Nueva descripción", response.getDescripcion());
        assertEquals("Grupo actualizado correctamente", response.getMensaje());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP025 — Miembro regular no puede editar el grupo
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP025 - Miembro regular intenta editar grupo y recibe error")
    void actualizarHogar_ComoMiembroRegular_DebeLanzarExcepcion() {

        ActualizarHogarRequest request = new ActualizarHogarRequest();
        request.setNombre("Nombre Inválido");

        when(hogarRepository.findById(10L)).thenReturn(Optional.of(hogar));
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "miembro@test.com"))
                .thenReturn(Optional.of(miembroRegular));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> hogarService.actualizarHogar(10L, "miembro@test.com", request)
        );

        assertEquals("Solo el administrador puede editar el grupo", ex.getMessage());
        verify(hogarRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP026 — Admin no puede guardar el grupo con nombre vacío
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP026 - Editar hogar con nombre vacío lanza BadRequestException")
    void actualizarHogar_ConNombreVacio_DebeLanzarExcepcion() {

        ActualizarHogarRequest request = new ActualizarHogarRequest();
        request.setNombre(""); // campo obligatorio vacío

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> hogarService.actualizarHogar(10L, "admin@test.com", request)
        );

        assertEquals("El nombre del grupo es obligatorio", ex.getMessage());
        verify(hogarRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP027 — Admin elimina el grupo (PASA PARCIALMENTE: las tareas no se borran)
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP027 - Admin elimina el grupo y sus miembros son desvinculados")
    void eliminarHogar_ComoAdmin_DebeEliminarHogarYMiembros() {

        when(hogarRepository.findById(10L)).thenReturn(Optional.of(hogar));
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "admin@test.com"))
                .thenReturn(Optional.of(miembroAdmin));
        when(miembroHogarRepository.findByHogarId(10L))
                .thenReturn(List.of(miembroAdmin, miembroRegular));

        hogarService.eliminarHogar(10L, "admin@test.com");

        // Verifica que se eliminaron todos los miembros
        verify(miembroHogarRepository, times(1))
                .deleteAll(List.of(miembroAdmin, miembroRegular));

        // Verifica que el hogar fue eliminado
        verify(hogarRepository, times(1)).delete(hogar);

        // NOTA: las tareas asociadas al hogar NO se eliminan explícitamente
        // en el código actual (falta tareaRepository.deleteByHogar).
        // Ese es el motivo por el que CP027 pasa solo parcialmente.
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP028 — Miembro regular no puede eliminar el grupo
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP028 - Miembro regular intenta eliminar grupo y recibe error")
    void eliminarHogar_ComoMiembroRegular_DebeLanzarExcepcion() {

        when(hogarRepository.findById(10L)).thenReturn(Optional.of(hogar));
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "miembro@test.com"))
                .thenReturn(Optional.of(miembroRegular));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> hogarService.eliminarHogar(10L, "miembro@test.com")
        );

        assertEquals("Solo el administrador puede eliminar el grupo", ex.getMessage());
        verify(hogarRepository, never()).delete(any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP021 — Miembro regular abandona el grupo
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP021 - Miembro regular abandona el grupo y es removido")
    void abandonarGrupo_MiembroRegular_DebeRemoverloDelGrupo() {

        when(hogarRepository.findById(10L)).thenReturn(Optional.of(hogar));
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "miembro@test.com"))
                .thenReturn(Optional.of(miembroRegular));
        // Quedan otros miembros después de que se va
        when(miembroHogarRepository.findByHogarId(10L))
                .thenReturn(List.of(miembroAdmin));

        hogarService.abandonarGrupo(10L, "miembro@test.com");

        verify(miembroHogarRepository, times(1)).delete(miembroRegular);
        // Como no era admin, NO se reasigna ningún administrador
        verify(miembroHogarRepository, never()).save(any());
        // El hogar NO se elimina porque quedan miembros
        verify(hogarRepository, never()).delete(any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP022 — Único admin abandona con otros miembros: se reasigna el rol
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP022 - Único admin abandona y el rol se reasigna al primer miembro")
    void abandonarGrupo_UnicoAdmin_DebeReasignarAdministrador() {

        when(hogarRepository.findById(10L)).thenReturn(Optional.of(hogar));
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "admin@test.com"))
                .thenReturn(Optional.of(miembroAdmin));
        // Después de que se va el admin, queda el miembro regular
        when(miembroHogarRepository.findByHogarId(10L))
                .thenReturn(List.of(miembroRegular));

        hogarService.abandonarGrupo(10L, "admin@test.com");

        verify(miembroHogarRepository, times(1)).delete(miembroAdmin);

        // El miembro restante debe ser promovido a administrador
        verify(miembroHogarRepository, times(1)).save(argThat(m ->
                Boolean.TRUE.equals(m.getEsAdministrador())
                        && m.getUsuario().getEmail().equals("miembro@test.com")
        ));

        // El hogar NO se elimina porque quedan miembros
        verify(hogarRepository, never()).delete(any());
    }

    // ────────────────────────────────────────────────────────────────────────
    // CP023 — Último miembro abandona: el grupo se elimina automáticamente
    // ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP023 - Último miembro abandona y el grupo se elimina de la BD")
    void abandonarGrupo_UltimoMiembro_DebeEliminarElGrupo() {

        when(hogarRepository.findById(10L)).thenReturn(Optional.of(hogar));
        when(miembroHogarRepository.findByHogarIdAndUsuarioEmail(10L, "admin@test.com"))
                .thenReturn(Optional.of(miembroAdmin));
        // No quedan miembros después de que se va
        when(miembroHogarRepository.findByHogarId(10L))
                .thenReturn(List.of());

        hogarService.abandonarGrupo(10L, "admin@test.com");

        verify(miembroHogarRepository, times(1)).delete(miembroAdmin);

        // El grupo debe eliminarse automáticamente
        verify(hogarRepository, times(1)).delete(hogar);

        // No debe reasignarse ningún admin porque no hay nadie
        verify(miembroHogarRepository, never()).save(any());
    }
}