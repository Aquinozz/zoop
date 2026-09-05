package pet_link.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pet_link.dtos.ReviewRequestDTO;
import pet_link.dtos.ReviewResponseDTO;
import pet_link.enums.AppointmentStatus;
import pet_link.exceptions.BadRequestException;
import pet_link.exceptions.ForbiddenException;
import pet_link.exceptions.ResourceNotFoundException;
import pet_link.models.AppointmentModel;
import pet_link.models.PrestadorModel;
import pet_link.models.ReviewModel;
import pet_link.models.RolesEntity;
import pet_link.models.Users;
import pet_link.repositories.AppointmentRepository;
import pet_link.repositories.PrestadorRepository;
import pet_link.repositories.ReviewRepository;
import pet_link.repositories.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PrestadorRepository prestadorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private ReviewService service;

    private Users tutor;
    private Users prestador;
    private PrestadorModel perfil;

    @BeforeEach
    void setUp() {
        tutor = usuario(1L, "ROLE_TUTOR");
        prestador = usuario(2L, "ROLE_PROFISSIONAL");
        perfil = new PrestadorModel();
        perfil.setId(10L);
        perfil.setNome("Clínica Teste");
        perfil.setUser(prestador);
        prestador.setPrestador(perfil);
    }

    private Users usuario(Long id, String role) {
        return Users.builder()
                .id(id)
                .nome("Usuário " + id)
                .email("user" + id + "@test.com")
                .senha("x")
                .roles(Set.of(new RolesEntity(role)))
                .build();
    }

    private ReviewRequestDTO dto(Long tutorId, Long prestadorId, int nota) {
        ReviewRequestDTO dto = new ReviewRequestDTO();
        dto.setTutorId(tutorId);
        dto.setPrestadorId(prestadorId);
        dto.setAgendamentoId(1L);
        dto.setNota(nota);
        dto.setComentario("Ótimo atendimento");
        return dto;
    }

    private AppointmentModel agendamento(AppointmentStatus status) {
        AppointmentModel app = new AppointmentModel();
        app.setId(1L);
        app.setTutor(tutor);
        app.setPrestador(prestador);
        app.setStatus(status);
        return app;
    }

    private void mockConsultaValida(AppointmentModel app) {
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(appointmentRepository.findById(app.getId())).thenReturn(Optional.of(app));
        when(reviewRepository.existsByAgendamento_Id(app.getId())).thenReturn(false);
        when(userRepository.findById(prestador.getId())).thenReturn(Optional.of(prestador));
    }

    @Test
    void criar_quandoTutorIdDivergente_lancaForbidden() {
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));

        assertThatThrownBy(() -> service.criar(dto(99L, prestador.getId(), 5), tutor.getEmail()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void criar_autoavaliacao_lancaBadRequest() {
        AppointmentModel app = agendamento(AppointmentStatus.FINALIZADO);
        app.setPrestador(tutor);
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(appointmentRepository.findById(app.getId())).thenReturn(Optional.of(app));
        when(reviewRepository.existsByAgendamento_Id(app.getId())).thenReturn(false);
        when(userRepository.findById(tutor.getId())).thenReturn(Optional.of(tutor));

        assertThatThrownBy(() -> service.criar(dto(tutor.getId(), tutor.getId(), 5), tutor.getEmail()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void criar_quandoConsultaNaoFinalizada_lancaBadRequest() {
        AppointmentModel app = agendamento(AppointmentStatus.CONFIRMADO);
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(appointmentRepository.findById(app.getId())).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.criar(dto(tutor.getId(), prestador.getId(), 5), tutor.getEmail()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void criar_quandoConsultaDeOutroTutor_lancaForbidden() {
        Users outro = usuario(99L, "ROLE_TUTOR");
        AppointmentModel app = agendamento(AppointmentStatus.FINALIZADO);
        app.setTutor(outro);
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(appointmentRepository.findById(app.getId())).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.criar(dto(tutor.getId(), prestador.getId(), 5), tutor.getEmail()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void criar_quandoConsultaJaAvaliada_lancaBadRequest() {
        AppointmentModel app = agendamento(AppointmentStatus.FINALIZADO);
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(appointmentRepository.findById(app.getId())).thenReturn(Optional.of(app));
        when(reviewRepository.existsByAgendamento_Id(app.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.criar(dto(tutor.getId(), prestador.getId(), 5), tutor.getEmail()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void criar_quandoConsultaInexistente_lancaNotFound() {
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(dto(tutor.getId(), prestador.getId(), 5), tutor.getEmail()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void criar_quandoPrestadorDivergenteDaConsulta_lancaBadRequest() {
        Users outroProfissional = usuario(77L, "ROLE_PROFISSIONAL");
        PrestadorModel outroPerfil = new PrestadorModel();
        outroPerfil.setId(20L);
        outroPerfil.setUser(outroProfissional);
        outroProfissional.setPrestador(outroPerfil);

        AppointmentModel app = agendamento(AppointmentStatus.FINALIZADO);
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(appointmentRepository.findById(app.getId())).thenReturn(Optional.of(app));
        when(reviewRepository.existsByAgendamento_Id(app.getId())).thenReturn(false);
        when(userRepository.findById(outroProfissional.getId())).thenReturn(Optional.of(outroProfissional));

        assertThatThrownBy(() -> service.criar(dto(tutor.getId(), outroProfissional.getId(), 5), tutor.getEmail()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void criar_ok_vinculaAgendamentoFinalizado() {
        AppointmentModel app = agendamento(AppointmentStatus.FINALIZADO);
        mockConsultaValida(app);
        when(reviewRepository.save(any(ReviewModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.findAverageNotaByPrestadorId(perfil.getId())).thenReturn(Optional.empty());
        when(prestadorRepository.save(any(PrestadorModel.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponseDTO result = service.criar(dto(tutor.getId(), prestador.getId(), 5), tutor.getEmail());

        assertThat(result).isNotNull();
        assertThat(result.getAgendamentoId()).isEqualTo(1L);
        verify(reviewRepository).save(any(ReviewModel.class));
    }

    @Test
    void listar_tutorUsaRepositorioDoTutor() {
        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(reviewRepository.findByTutor_Id(tutor.getId())).thenReturn(List.of());

        service.listarTodos(tutor.getEmail());

        verify(reviewRepository).findByTutor_Id(tutor.getId());
    }

    @Test
    void listar_prestadorUsaRepositorioDoPrestador() {
        when(userRepository.findByEmail(prestador.getEmail())).thenReturn(Optional.of(prestador));
        when(reviewRepository.findByPrestador_User_Id(prestador.getId())).thenReturn(List.of());

        service.listarTodos(prestador.getEmail());

        verify(reviewRepository).findByPrestador_User_Id(prestador.getId());
    }

    @Test
    void listar_adminUsaFindAll() {
        Users admin = usuario(1L, "ROLE_ADMIN");
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(reviewRepository.findAll()).thenReturn(List.of());

        service.listarTodos(admin.getEmail());

        verify(reviewRepository).findAll();
    }

    @Test
    void deletar_quandoNaoAutor_lancaForbidden() {
        Users outro = usuario(99L, "ROLE_TUTOR");
        ReviewModel review = new ReviewModel();
        review.setId(1L);
        review.setTutor(outro);
        review.setPrestador(perfil);

        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.deletar(1L, tutor.getEmail()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletar_autorPodeRemover() {
        ReviewModel review = new ReviewModel();
        review.setId(1L);
        review.setTutor(tutor);
        review.setPrestador(perfil);

        when(userRepository.findByEmail(tutor.getEmail())).thenReturn(Optional.of(tutor));
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.findAverageNotaByPrestadorId(perfil.getId())).thenReturn(Optional.empty());
        when(prestadorRepository.save(any(PrestadorModel.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deletar(1L, tutor.getEmail());

        verify(reviewRepository).delete(review);
    }
}