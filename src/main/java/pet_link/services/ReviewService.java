package pet_link.services;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pet_link.dtos.ReviewRequestDTO;
import pet_link.dtos.ReviewResponseDTO;
import pet_link.enums.AppointmentStatus;
import pet_link.enums.UserRole;
import pet_link.exceptions.BadRequestException;
import pet_link.exceptions.ForbiddenException;
import pet_link.exceptions.ResourceNotFoundException;
import pet_link.models.AppointmentModel;
import pet_link.models.PrestadorModel;
import pet_link.models.ReviewModel;
import pet_link.models.Users;
import pet_link.repositories.AppointmentRepository;
import pet_link.repositories.PrestadorRepository;
import pet_link.repositories.ReviewRepository;
import pet_link.repositories.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PrestadorRepository prestadorRepository;
    private final AppointmentRepository appointmentRepository;

    private Users usuarioAtual(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }

    private boolean temRole(Users user, UserRole role) {
        return user.getRoles().stream().anyMatch(r -> r.getAuthority().equals(role.name()));
    }

    public ReviewResponseDTO criar(@Valid ReviewRequestDTO dto, String email) {
        log.info("Criando avaliação para prestador {}", dto.getPrestadorId());

        Users tutor = usuarioAtual(email);

        if (dto.getTutorId() != null && !dto.getTutorId().equals(tutor.getId())) {
            throw new ForbiddenException("Só é possível avaliar como o seu próprio usuário.");
        }

        AppointmentModel agendamento = appointmentRepository.findById(dto.getAgendamentoId())
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento com ID " + dto.getAgendamentoId() + " não encontrado."));

        if (agendamento.getTutor() == null || !agendamento.getTutor().getId().equals(tutor.getId())) {
            throw new ForbiddenException("Você só pode avaliar consultas da sua própria conta.");
        }

        if (agendamento.getStatus() != AppointmentStatus.FINALIZADO) {
            throw new BadRequestException("Só é possível avaliar consultas concluídas. O profissional precisa confirmar que o atendimento foi realizado.");
        }

        if (reviewRepository.existsByAgendamento_Id(agendamento.getId())) {
            throw new BadRequestException("Esta consulta já foi avaliada.");
        }

        Users prestadorUsuario = userRepository.findById(dto.getPrestadorId())
                .orElseThrow(() -> new ResourceNotFoundException("Profissional com ID " + dto.getPrestadorId() + " não encontrado."));

        if (agendamento.getPrestador() == null || !agendamento.getPrestador().getId().equals(prestadorUsuario.getId())) {
            throw new BadRequestException("O profissional informado não corresponde ao atendimento desta consulta.");
        }

        if (prestadorUsuario.getId().equals(tutor.getId())) {
            throw new BadRequestException("Você não pode avaliar a si mesmo.");
        }

        PrestadorModel prestador = prestadorUsuario.getPrestador();
        if (prestador == null) {
            throw new ResourceNotFoundException("Prestador vinculado ao usuário com ID " + dto.getPrestadorId() + " não encontrado.");
        }

        ReviewModel review = new ReviewModel();
        review.setTutor(tutor);
        review.setPrestador(prestador);
        review.setAgendamento(agendamento);
        review.setNota(dto.getNota());
        review.setComentario(dto.getComentario());
        review.setDataCriacao(LocalDateTime.now());

        ReviewModel reviewSalva = reviewRepository.save(review);

        atualizarMediaPrestador(prestador);

        log.info("Review criada com sucesso. Id={}", reviewSalva.getId());

        return new ReviewResponseDTO(reviewSalva);
    }

    public List<ReviewResponseDTO> listarTodos(String email) {
        Users current = usuarioAtual(email);

        List<ReviewModel> reviews;
        if (temRole(current, UserRole.ROLE_ADMIN)) {
            reviews = reviewRepository.findAll();
        } else if (temRole(current, UserRole.ROLE_PROFISSIONAL)) {
            reviews = reviewRepository.findByPrestador_User_Id(current.getId());
        } else if (temRole(current, UserRole.ROLE_TUTOR)) {
            reviews = reviewRepository.findByTutor_Id(current.getId());
        } else {
            throw new ForbiddenException("Usuário sem permissão para listar avaliações.");
        }

        return reviews.stream()
                .map(ReviewResponseDTO::new)
                .toList();
    }

    public void deletar(Long id, String email) {
        Users current = usuarioAtual(email);

        ReviewModel review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação com ID " + id + " não encontrada."));

        boolean autor = review.getTutor() != null && review.getTutor().getId().equals(current.getId());
        if (!autor && !temRole(current, UserRole.ROLE_ADMIN)) {
            throw new ForbiddenException("Você não tem permissão para remover esta avaliação.");
        }

        PrestadorModel prestador = review.getPrestador();

        reviewRepository.delete(review);

        atualizarMediaPrestador(prestador);
        log.info("Review ID {} deletada com sucesso.", id);
    }

    private void atualizarMediaPrestador(PrestadorModel prestador) {
        double media = reviewRepository.findAverageNotaByPrestadorId(prestador.getId()).orElse(0.0);

        prestador.setAvaliacaoMedia(media);
        prestadorRepository.save(prestador);

        log.info("Média do prestador {} atualizada para {}", prestador.getId(), media);
    }
}