package ca.vina.nexu.partage.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Les refus de validation, dans la forme du Node : {@code {"errors": ["… ; …"]}}, code 400, un
 * message par règle enfreinte. La validation vit sur les DTO et les paramètres (Bean Validation)
 * — à la frontière, avant tout accès au métier — et ses messages sont des phrases complètes,
 * les mêmes que le Node : le front les affiche telles quelles.
 *
 * <p>Les messages sont triés par ordre de déclaration des champs, pour qu'un même corps fautif
 * produise toujours la même réponse : un ensemble de violations n'a pas d'ordre, une réponse
 * HTTP en a un.
 */
@RestControllerAdvice
public class ErreursValidation {

    private static ResponseEntity<Object> refus(List<String> messages) {
        return ResponseEntity.badRequest().body(Map.of("errors", List.of(String.join("; ", messages))));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> corpsInvalide(MethodArgumentNotValidException e) {
        Class<?> cible = e.getTarget() == null ? null : e.getTarget().getClass();
        List<FieldError> erreurs = new ArrayList<>(e.getBindingResult().getFieldErrors());
        erreurs.sort(Comparator.comparingInt((FieldError f) -> rang(cible, f.getField())).thenComparing(FieldError::getField));
        List<String> messages = new ArrayList<>(erreurs.stream().map(FieldError::getDefaultMessage).toList());
        e.getBindingResult().getGlobalErrors().forEach(g -> messages.add(g.getDefaultMessage()));
        return refus(messages);
    }

    /**
     * Contraintes posées sur les paramètres de chemin et de requête — et, dès qu'une méthode en
     * porte une, son corps {@code @Valid} arrive aussi par ici (Spring valide alors la méthode
     * entière). Les erreurs de champ y sont triées comme pour un corps seul.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Object> parametresInvalides(HandlerMethodValidationException e) {
        List<String> messages = new ArrayList<>();
        for (var resultat : e.getParameterValidationResults()) {
            if (resultat instanceof ParameterErrors erreurs) {
                Class<?> cible = erreurs.getArgument() == null ? null : erreurs.getArgument().getClass();
                List<FieldError> champs = new ArrayList<>(erreurs.getFieldErrors());
                champs.sort(Comparator.comparingInt((FieldError f) -> rang(cible, f.getField())).thenComparing(FieldError::getField));
                champs.forEach(f -> messages.add(f.getDefaultMessage()));
                erreurs.getGlobalErrors().forEach(g -> messages.add(g.getDefaultMessage()));
                continue;
            }
            for (MessageSourceResolvable r : resultat.getResolvableErrors()) {
                messages.add(r.getDefaultMessage());
            }
        }
        return refus(messages);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> contraintesViolees(ConstraintViolationException e) {
        return refus(e.getConstraintViolations().stream().map(ConstraintViolation::getMessage).sorted().toList());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> corpsIllisible(HttpMessageNotReadableException e) {
        return refus(List.of("corps de requête illisible : JSON attendu"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> typeInattendu(MethodArgumentTypeMismatchException e) {
        return refus(List.of("paramètre « " + e.getName() + " » d'un type inattendu"));
    }

    /** L'indice du champ dans le record — sa position de déclaration ; absent, il passe derrière. */
    static int rang(Class<?> cible, String champ) {
        if (cible == null || !cible.isRecord()) {
            return Integer.MAX_VALUE;
        }
        var composants = cible.getRecordComponents();
        for (int i = 0; i < composants.length; i++) {
            if (composants[i].getName().equals(champ)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }
}
