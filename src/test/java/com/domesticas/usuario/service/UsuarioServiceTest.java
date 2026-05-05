package com.domesticas.usuario.service;

import com.domesticas.exception.BadRequestException;
import com.domesticas.hogar.model.Hogar;
import com.domesticas.hogar.model.MiembroHogar;
import com.domesticas.hogar.model.Rol;
import com.domesticas.hogar.repository.MiembroHogarRepository;
import com.domesticas.usuario.dto.request.ActualizarPerfilRequest;
import com.domesticas.usuario.dto.response.UsuarioResponse;
import com.domesticas.usuario.model.Usuario;
import com.domesticas.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
        import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private MiembroHogarRepository miembroHogarRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuarioFalso;
    private ActualizarPerfilRequest requestActualizacion;

    @BeforeEach
    void setUp() {
        // Usuario base que simula lo que devuelve la BD
        usuarioFalso = Usuario.builder()
                .id(1L)
                .nombre("Juan Test")
                .email("juan@test.com")
                .password("$2a$10$hashFalso")
                .build();

        // Request base para actualizar perfil
        requestActualizacion = new ActualizarPerfilRequest();
        requestActualizacion.setNombre("Juan Actualizado");
        requestActualizacion.setEmail("juan@test.com");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CP007 — Visualización de perfil: muestra nombre, correo y grupos
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP007a - Obtener perfil retorna nombre, correo y lista de grupos")
    void obtenerPerfil_UsuarioConGrupos_DebeRetornarDatosCompletos() {

        // Construimos Hogar y Rol reales con builder (ya tenemos los paquetes exactos)
        Hogar hogarReal = Hogar.builder()
                .id(10L)
                .nombre("Hogar Test")
                .codigoAcceso("ABC123")
                .build();

        Rol rolReal = Rol.builder()
                .id(1L)
                .nombre("Padre")
                .build();

        MiembroHogar miembroFalso = MiembroHogar.builder()
                .id(1L)
                .usuario(usuarioFalso)
                .hogar(hogarReal)
                .rol(rolReal)
                .esAdministrador(true)
                .build();

        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.of(usuarioFalso));
        when(miembroHogarRepository.findByUsuario(usuarioFalso))
                .thenReturn(List.of(miembroFalso));

        UsuarioResponse response = usuarioService.obtenerPerfil("juan@test.com");

        assertNotNull(response);
        assertEquals("Juan Test", response.getNombre());
        assertEquals("juan@test.com", response.getEmail());
        assertNotNull(response.getGrupos());
        assertEquals(1, response.getGrupos().size());
        assertEquals("Hogar Test", response.getGrupos().get(0).getHogar());
        assertEquals("Padre", response.getGrupos().get(0).getRol());
        assertTrue(response.getGrupos().get(0).getEsAdministrador());
    }

    @Test
    @DisplayName("CP007b - Obtener perfil de usuario sin grupos retorna lista vacía")
    void obtenerPerfil_UsuarioSinGrupos_DebeRetornarListaVacia() {

        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.of(usuarioFalso));
        when(miembroHogarRepository.findByUsuario(usuarioFalso))
                .thenReturn(List.of()); // sin grupos

        UsuarioResponse response = usuarioService.obtenerPerfil("juan@test.com");

        assertNotNull(response);
        assertEquals("Juan Test", response.getNombre());
        assertTrue(response.getGrupos().isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CP008 — Edición exitosa del nombre
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP008 - Actualizar nombre con datos válidos lo guarda correctamente")
    void actualizarPerfil_ConNombreValido_DebeActualizarNombre() {

        // ARRANGE
        requestActualizacion.setNombre("Juan Actualizado");
        requestActualizacion.setEmail("juan@test.com"); // mismo correo, no hay conflicto

        Usuario usuarioActualizado = Usuario.builder()
                .id(1L)
                .nombre("Juan Actualizado")
                .email("juan@test.com")
                .password("$2a$10$hashFalso")
                .build();

        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.of(usuarioFalso));
        when(usuarioRepository.save(any(Usuario.class)))
                .thenReturn(usuarioActualizado);

        // ACT
        UsuarioResponse response = usuarioService.actualizarPerfil(
                "juan@test.com", requestActualizacion
        );

        // ASSERT
        assertNotNull(response);
        assertEquals("Juan Actualizado", response.getNombre());
        assertEquals("juan@test.com", response.getEmail());
        assertEquals("Perfil actualizado correctamente", response.getMensaje());

        verify(usuarioRepository, times(1)).save(any(Usuario.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CP009 — Campo obligatorio vacío impide guardar cambios
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP009a - Nombre vacío lanza BadRequestException")
    void actualizarPerfil_ConNombreVacio_DebeLanzarExcepcion() {

        requestActualizacion.setNombre(""); // nombre en blanco

        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.of(usuarioFalso));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> usuarioService.actualizarPerfil("juan@test.com", requestActualizacion)
        );

        assertEquals("El nombre es obligatorio", ex.getMessage());

        // No debe guardarse nada
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP009b - Nombre null lanza BadRequestException")
    void actualizarPerfil_ConNombreNull_DebeLanzarExcepcion() {

        requestActualizacion.setNombre(null);

        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.of(usuarioFalso));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> usuarioService.actualizarPerfil("juan@test.com", requestActualizacion)
        );

        assertEquals("El nombre es obligatorio", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP009c - Correo vacío lanza BadRequestException")
    void actualizarPerfil_ConEmailVacio_DebeLanzarExcepcion() {

        requestActualizacion.setNombre("Juan Test");
        requestActualizacion.setEmail(""); // correo en blanco

        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.of(usuarioFalso));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> usuarioService.actualizarPerfil("juan@test.com", requestActualizacion)
        );

        assertEquals("El correo es obligatorio", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CP010 — Correo ya usado por otro usuario impide el cambio
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CP010 - Correo en uso por otro usuario lanza BadRequestException")
    void actualizarPerfil_ConCorreoDuplicado_DebeLanzarExcepcion() {

        // El usuario actual tiene juan@test.com
        // Intenta cambiarlo a otro@test.com que ya existe
        requestActualizacion.setNombre("Juan Test");
        requestActualizacion.setEmail("otro@test.com");

        Usuario otroUsuario = Usuario.builder()
                .id(2L)
                .nombre("Otro Usuario")
                .email("otro@test.com")
                .password("$2a$10$otroHash")
                .build();

        // Devuelve el usuario actual al buscar por email actual
        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.of(usuarioFalso));

        // El correo nuevo ya existe (pertenece a otro usuario)
        when(usuarioRepository.findByEmail("otro@test.com"))
                .thenReturn(Optional.of(otroUsuario));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> usuarioService.actualizarPerfil("juan@test.com", requestActualizacion)
        );

        assertEquals("El correo ya está en uso", ex.getMessage());

        // El perfil no debe guardarse
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP010b - Guardar el mismo correo actual no lanza excepción")
    void actualizarPerfil_ConMismoCorreo_NoDebeVerificarDuplicado() {

        // El usuario no cambia su correo, así que no debe validarse duplicado
        requestActualizacion.setNombre("Nuevo Nombre");
        requestActualizacion.setEmail("juan@test.com"); // mismo correo actual

        Usuario usuarioActualizado = Usuario.builder()
                .id(1L)
                .nombre("Nuevo Nombre")
                .email("juan@test.com")
                .password("$2a$10$hashFalso")
                .build();

        when(usuarioRepository.findByEmail("juan@test.com"))
                .thenReturn(Optional.of(usuarioFalso));
        when(usuarioRepository.save(any(Usuario.class)))
                .thenReturn(usuarioActualizado);

        UsuarioResponse response = usuarioService.actualizarPerfil(
                "juan@test.com", requestActualizacion
        );

        assertEquals("Nuevo Nombre", response.getNombre());

        // findByEmail del correo nuevo NO debe llamarse porque es el mismo
        // (solo se llama 1 vez: para encontrar al usuario actual)
        verify(usuarioRepository, times(1)).findByEmail("juan@test.com");
    }
}
