package pet_link.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pet_link.models.ReviewModel;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<ReviewModel, Long> {

    @Query("select avg(r.nota) from ReviewModel r where r.prestador.id = :prestadorId")
    Optional<Double> findAverageNotaByPrestadorId(@Param("prestadorId") Long prestadorId);

    @EntityGraph(attributePaths = {"tutor", "prestador", "agendamento"})
    List<ReviewModel> findByTutor_Id(Long tutorId);

    @EntityGraph(attributePaths = {"tutor", "prestador", "agendamento"})
    List<ReviewModel> findByPrestador_User_Id(Long userId);

    @Override
    @EntityGraph(attributePaths = {"tutor", "prestador", "agendamento"})
    List<ReviewModel> findAll();

    boolean existsByAgendamento_Id(Long agendamentoId);
}