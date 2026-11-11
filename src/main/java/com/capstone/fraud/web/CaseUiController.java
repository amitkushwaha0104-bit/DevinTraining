package com.capstone.fraud.web;

import com.capstone.fraud.api.dto.CaseIntakeRequest;
import com.capstone.fraud.api.dto.ReviewRequest;
import com.capstone.fraud.application.*;
import com.capstone.fraud.domain.CaseStatus;
import com.capstone.fraud.knowledge.PolicyIngestionService;
import com.capstone.fraud.knowledge.PolicyRetriever;
import com.capstone.fraud.persistence.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Thymeleaf UI: intake, investigation workspace, evidence, citations,
 * follow-up and human review. Controllers call the same application
 * services as the REST API.
 */
@Controller
public class CaseUiController {

    private final FraudCaseService caseService;
    private final InvestigationOrchestrator orchestrator;
    private final CaseQaService qaService;
    private final PolicyIngestionService policyIngestion;
    private final PolicyRetriever policyRetriever;

    public CaseUiController(FraudCaseService caseService,
                            InvestigationOrchestrator orchestrator,
                            CaseQaService qaService,
                            PolicyIngestionService policyIngestion,
                            PolicyRetriever policyRetriever) {
        this.caseService = caseService;
        this.orchestrator = orchestrator;
        this.qaService = qaService;
        this.policyIngestion = policyIngestion;
        this.policyRetriever = policyRetriever;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/cases";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/cases")
    public String cases(Model model) {
        model.addAttribute("cases", caseService.listCases());
        return "cases";
    }

    @GetMapping("/cases/new")
    public String intakeForm(Model model) {
        model.addAttribute("form", new IntakeForm());
        return "intake";
    }

    @PostMapping("/cases")
    public String createCase(@Valid @ModelAttribute("form") IntakeForm form,
                             BindingResult binding,
                             Authentication auth,
                             RedirectAttributes redirect,
                             Model model) {
        if (binding.hasErrors()) {
            return "intake";
        }
        try {
            FraudCaseEntity entity = caseService.createCase(form.toRequest(), auth.getName());
            redirect.addFlashAttribute("message", "Case " + entity.getCaseId() + " created (synthetic data).");
            return "redirect:/cases/" + entity.getCaseId();
        } catch (IntakeValidationException e) {
            model.addAttribute("error", e.getMessage());
            return "intake";
        }
    }

    @GetMapping("/cases/{caseId}")
    public String workspace(@PathVariable String caseId, Model model) {
        populateWorkspace(caseId, model);
        return "workspace";
    }

    @PostMapping("/cases/{caseId}/investigate")
    public String investigate(@PathVariable String caseId, Authentication auth, RedirectAttributes redirect) {
        try {
            var result = orchestrator.investigate(caseId, auth.getName());
            redirect.addFlashAttribute("message",
                    "Investigation complete: " + result.recommendation().getClassification()
                            + " → " + result.recommendation().getRecommendedRoute());
        } catch (InvalidCaseStateException | IntakeValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + caseId;
    }

    @GetMapping("/cases/{caseId}/evidence")
    public String evidence(@PathVariable String caseId, Model model) {
        FraudCaseEntity entity = caseService.requireCase(caseId);
        model.addAttribute("entity", entity);
        model.addAttribute("evidence", caseService.evidenceFor(entity));
        model.addAttribute("traces", caseService.tracesFor(entity));
        return "evidence";
    }

    @GetMapping("/cases/{caseId}/citations")
    public String citations(@PathVariable String caseId, Model model) {
        FraudCaseEntity entity = caseService.requireCase(caseId);
        RecommendationEntity rec = caseService.latestRecommendation(entity);
        model.addAttribute("entity", entity);
        model.addAttribute("citations", rec == null ? List.of() : caseService.citationsFor(rec));
        model.addAttribute("policyDocs", policyIngestion.activeDocuments());
        model.addAttribute("topK", policyRetriever.topK());
        model.addAttribute("threshold", policyRetriever.threshold());
        return "citations";
    }

    @GetMapping("/cases/{caseId}/review")
    public String reviewForm(@PathVariable String caseId, Model model) {
        FraudCaseEntity entity = caseService.requireCase(caseId);
        RecommendationEntity rec = caseService.latestRecommendation(entity);
        model.addAttribute("entity", entity);
        model.addAttribute("recommendation", rec);
        model.addAttribute("missing", rec == null ? List.of() : caseService.jsonToList(rec.getMissingInformationJson()));
        model.addAttribute("evidence", caseService.evidenceFor(entity));
        model.addAttribute("reviews", caseService.reviewsFor(entity));
        return "review";
    }

    @PostMapping("/cases/{caseId}/review")
    public String submitReview(@PathVariable String caseId,
                               @RequestParam String action,
                               @RequestParam(required = false) String selectedRoute,
                               @RequestParam String reason,
                               Authentication auth,
                               RedirectAttributes redirect,
                               Model model) {
        if (reason == null || reason.isBlank()) {
            redirect.addFlashAttribute("error", "Reviewer rationale is mandatory.");
            return "redirect:/cases/" + caseId + "/review";
        }
        try {
            caseService.recordReview(caseId, new ReviewRequest(action, selectedRoute, reason), auth.getName());
            redirect.addFlashAttribute("message", "Disposition recorded and audited.");
            return "redirect:/cases/" + caseId;
        } catch (InvalidCaseStateException | IntakeValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/cases/" + caseId + "/review";
        }
    }

    @PostMapping("/cases/{caseId}/messages")
    public String followUp(@PathVariable String caseId,
                           @RequestParam String message,
                           Authentication auth,
                           RedirectAttributes redirect) {
        var response = qaService.ask(caseId, message, auth.getName());
        redirect.addFlashAttribute("qaAnswer", response.answer());
        redirect.addFlashAttribute("qaCitations", response.citations());
        return "redirect:/cases/" + caseId + "#followup";
    }

    @GetMapping("/cases/{caseId}/audit")
    public String audit(@PathVariable String caseId, Model model) {
        FraudCaseEntity entity = caseService.requireCase(caseId);
        model.addAttribute("entity", entity);
        model.addAttribute("events", caseService.auditFor(entity));
        return "audit";
    }

    private void populateWorkspace(String caseId, Model model) {
        FraudCaseEntity entity = caseService.requireCase(caseId);
        RecommendationEntity rec = caseService.latestRecommendation(entity);
        model.addAttribute("entity", entity);
        model.addAttribute("claim", caseService.claimFor(entity));
        model.addAttribute("plan", InvestigationOrchestrator.EVIDENCE_PLAN);
        model.addAttribute("evidence", caseService.evidenceFor(entity));
        model.addAttribute("traces", caseService.tracesFor(entity));
        model.addAttribute("recommendation", rec);
        model.addAttribute("missing", rec == null ? List.of() : caseService.jsonToList(rec.getMissingInformationJson()));
        model.addAttribute("rules", rec == null ? List.of() : caseService.jsonToList(rec.getRuleIdsJson()));
        model.addAttribute("reviews", caseService.reviewsFor(entity));
        model.addAttribute("statuses", CaseStatus.values());
    }
}
