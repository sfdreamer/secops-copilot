package com.secops.evaluation;

import com.secops.ai.LlmAdapter;
import com.secops.ai.TriagePrompt;
import com.secops.ingestion.AlertFixtureLoader;
import com.secops.ingestion.FixtureLoadReport;
import com.secops.model.*;
import com.secops.repository.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * 全链路评估（API.md 13 节 / PRD 6 节）。
 * 本模块是唯一允许访问 Ground Truth 的业务模块；只读取实际运行产物，不触发模型调用。
 * 没有有效样本或没有真实计时数据时返回 null，不得使用硬编码指标。
 */
@Service
public class EvaluationService {

    private static final String DATASET_VERSION = "demo-v1";
    private static final List<String> LABELS = List.of("ATTACK", "BENIGN", "UNCERTAIN");

    public record Meta(String datasetVersion,int sampleCount,Instant evaluatedAt,String modelName,String promptVersion) {}
    public record DataQuality(int inputAlertCount,int parsedAlertCount,Double parseSuccessRate,Double requiredFieldCompleteness) {}
    public record Aggregation(int rawSshAlertCount,int incidentCount,Double compressionRate,Double precision,Double recall) {}
    /** uncertainSampleCount：PRD 6 要求 UNCERTAIN 单独统计，不计入二分类 sampleCount。 */
    public record BinaryMetric(int sampleCount,int uncertainSampleCount,Double precision,Double recall,Double f1) {}
    public record AiEvaluation(int sampleCount,Double accuracy,Double precision,Double recall,Double macroF1,
                               Map<String,Map<String,Long>> confusionMatrix) {}
    /** traceabilityStatus：分母不可还原时标记 NOT_COMPUTABLE，避免输出恒为 1.0 的失真指标。 */
    public record EvidenceEvaluation(Double traceabilityRate,String traceabilityStatus,
                                     Double semanticSupportRate,String semanticSupportStatus) {}
    public record AgentEvaluation(int toolCallCount,Double toolSuccessRate,Double parameterValidityRate,
                                  Double toolPlanPrecision,Double toolPlanRecall,Double toolPlanF1,
                                  Double averageToolCalls,Double averageDurationMs) {}
    public record BusinessEvaluation(int reviewSampleCount,Double adoptionRate,Double correctionRate,
                                     Double manualAverageDurationMs,Double aiAssistedAverageDurationMs,
                                     Double efficiencyImprovementRate,String measurementStatus) {}
    public record Result(Meta meta,DataQuality dataQuality,Aggregation aggregation,BinaryMetric ruleEvaluation,
                         AiEvaluation aiEvaluation,EvidenceEvaluation evidenceEvaluation,AgentEvaluation agentEvaluation,
                         BusinessEvaluation businessEvaluation) {}

    /** 符合场景级评估条件的事件：关联告警 scenarioId 唯一且存在对应标签（DATABASE.md 7.1）。 */
    private record Eligible(Incident incident,GroundTruth truth) {}

    private final AlertRepository alerts;
    private final IncidentRepository incidents;
    private final AIAnalysisRepository analyses;
    private final ToolCallTraceRepository traces;
    private final ReviewRepository reviews;
    private final EvaluationRepository groundTruth;
    private final AlertFixtureLoader fixtureLoader;
    private final LlmAdapter llm;

    public EvaluationService(AlertRepository alerts,IncidentRepository incidents,AIAnalysisRepository analyses,
            ToolCallTraceRepository traces,ReviewRepository reviews,EvaluationRepository groundTruth,
            AlertFixtureLoader fixtureLoader,LlmAdapter llm) {
        this.alerts=alerts; this.incidents=incidents; this.analyses=analyses; this.traces=traces;
        this.reviews=reviews; this.groundTruth=groundTruth; this.fixtureLoader=fixtureLoader; this.llm=llm;
    }

    public Result evaluate() {
        List<Incident> allIncidents=incidents.findAll();
        List<Eligible> eligible=allIncidents.stream().map(this::match).flatMap(Optional::stream).toList();

        return new Result(
                new Meta(DATASET_VERSION,eligible.size(),Instant.now(),llm.modelName(),TriagePrompt.VERSION),
                dataQuality(),
                aggregation(allIncidents),
                ruleEvaluation(eligible),
                aiEvaluation(eligible),
                evidenceEvaluation(),
                agentEvaluation(eligible),
                businessEvaluation());
    }

    private Optional<Eligible> match(Incident incident) {
        Set<String> scenarioIds=new HashSet<>();
        for (Alert a:alerts.findByIds(incident.alertIds()))
            if (a.scenarioId()!=null && !a.scenarioId().isBlank()) scenarioIds.add(a.scenarioId());
        if (scenarioIds.size()!=1) return Optional.empty();
        return groundTruth.findByScenarioId(scenarioIds.iterator().next()).map(gt -> new Eligible(incident,gt));
    }

    private DataQuality dataQuality() {
        FixtureLoadReport report=fixtureLoader.getReport();
        int read=report==null?0:report.readCount();
        int accepted=alerts.count();
        int rejected=report==null?0:report.rejectedCount();
        // 解析成功率按「通过必填字段与枚举校验」计算：重复 alertId 会被去重但并非解析失败，
        // 因此分子不能用去重后的入库数，否则重复数据会被误算成解析失败。
        Double parseRate=read>0?(double)(read-rejected)/read:null;
        // 必填字段完整率：原始记录中通过必填字段与枚举校验的比例（被拒绝记录即必填字段不完整或非法）
        Double completeness=read>0?(double)(read-rejected)/read:null;
        return new DataQuality(read,accepted,parseRate,completeness);
    }

    private Aggregation aggregation(List<Incident> allIncidents) {
        int rawSsh=(int)alerts.findAll().stream().filter(a -> "SSH_BRUTE_FORCE".equals(a.alertType())).count();
        int count=allIncidents.size();
        Double compression=rawSsh>0?(double)(rawSsh-count)/rawSsh:null;
        // 本期未准备独立关联标注，聚合 Precision/Recall 返回 null（API.md 13 节）
        return new Aggregation(rawSsh,count,compression,null,null);
    }

    /** 规则评估：ruleRiskScore >= 50 判为 ATTACK；UNCERTAIN 标签不纳入二分类，单独计数。 */
    private BinaryMetric ruleEvaluation(List<Eligible> eligible) {
        long tp=0,fp=0,fn=0; int samples=0,uncertain=0;
        for (Eligible e:eligible) {
            if ("UNCERTAIN".equals(e.truth().expectedLabel())) { uncertain++; continue; }
            samples++;
            boolean predicted=e.incident().riskAssessment().score()>=50;
            boolean actual="ATTACK".equals(e.truth().expectedLabel());
            if (predicted && actual) tp++;
            else if (predicted) fp++;
            else if (actual) fn++;
        }
        double[] prf=prf(tp,fp,fn);
        return new BinaryMetric(samples,uncertain,prf[0]<0?null:prf[0],prf[1]<0?null:prf[1],prf[2]<0?null:prf[2]);
    }

    /** AI 评估：只统计有成功且有效 AIAnalysis、并能匹配唯一 Ground Truth 的事件。 */
    private AiEvaluation aiEvaluation(List<Eligible> eligible) {
        Map<String,Map<String,Long>> matrix=new LinkedHashMap<>();
        for (String row:LABELS) {
            Map<String,Long> cols=new LinkedHashMap<>();
            for (String col:LABELS) cols.put(col,0L);
            matrix.put(row,cols);
        }
        int samples=0,correct=0;
        long tp=0,fp=0,fn=0;
        for (Eligible e:eligible) {
            AIAnalysis latest=latestSucceeded(e.incident().incidentId());
            if (latest==null || "INVALID".equals(latest.validationStatus()) || latest.verdict()==null) continue;
            String predictedLabel=switch (latest.verdict()) {
                case "SUSPICIOUS" -> "ATTACK";
                case "LIKELY_BENIGN" -> "BENIGN";
                default -> "UNCERTAIN";
            };
            String actualLabel=e.truth().expectedLabel();
            if (!LABELS.contains(actualLabel)) continue;
            samples++;
            matrix.get(actualLabel).merge(predictedLabel,1L,Long::sum);
            if (predictedLabel.equals(actualLabel)) correct++;
            boolean pa="ATTACK".equals(predictedLabel),aa="ATTACK".equals(actualLabel);
            if (pa && aa) tp++; else if (pa) fp++; else if (aa) fn++;
        }
        Double accuracy=samples>0?(double)correct/samples:null;
        double[] prf=prf(tp,fp,fn);
        return new AiEvaluation(samples,accuracy,prf[0]<0?null:prf[0],prf[1]<0?null:prf[1],
                macroF1(matrix),matrix);
    }

    /** 宏 F1：只在有真实或预测样本的类别上平均；无样本时返回 null。 */
    private Double macroF1(Map<String,Map<String,Long>> matrix) {
        double sum=0; int used=0;
        for (String label:LABELS) {
            long support=matrix.get(label).values().stream().mapToLong(Long::longValue).sum();
            long predicted=LABELS.stream().mapToLong(row -> matrix.get(row).get(label)).sum();
            if (support==0 && predicted==0) continue;
            long tp=matrix.get(label).get(label);
            double p=predicted>0?(double)tp/predicted:0;
            double r=support>0?(double)tp/support:0;
            sum+=(p+r)>0?2*p*r/(p+r):0;
            used++;
        }
        return used>0?sum/used:null;
    }

    /**
     * 证据可追溯率本期【不可计算】，返回 null + NOT_COMPUTABLE。
     * 原因：AgentResultValidator 在保存 AIAnalysis 之前已剔除无效引用，且未持久化「过滤前引用总数」，
     * 分母无法还原；若只遍历已保存的 evidence()，分子分母恒等，指标会恒为 1.0，属于失真数据。
     * 语义支持率需人工抽样，本期 NOT_EVALUATED。
     * 若要启用该指标，需先在领域模型中新增过滤前引用计数字段并同步 DATABASE.md / API.md。
     */
    private EvidenceEvaluation evidenceEvaluation() {
        return new EvidenceEvaluation(null,"NOT_COMPUTABLE",null,"NOT_EVALUATED");
    }

    /** Agent 指标只统计真实发生的工具调用；工具计划与 expectedToolPlan 比较，无有效样本返回 null。 */
    private AgentEvaluation agentEvaluation(List<Eligible> eligible) {
        List<ToolCallTrace> all=traces.findAll();
        int total=all.size();
        long success=all.stream().filter(t -> "SUCCESS".equals(t.executionStatus())).count();
        long rejected=all.stream().filter(t -> "REJECTED".equals(t.executionStatus())).count();

        long tp=0,fp=0,fn=0; int planSamples=0;
        for (Eligible e:eligible) {
            AIAnalysis latest=latestSucceeded(e.incident().incidentId());
            if (latest==null) continue;
            planSamples++;
            Set<String> actual=new HashSet<>();
            for (ToolCallTrace t:traces.findByIncident(e.incident().incidentId()))
                if (t.analysisId().equals(latest.analysisId()) && "SUCCESS".equals(t.executionStatus())) actual.add(t.toolName());
            Set<String> expected=new HashSet<>(e.truth().expectedToolPlan());
            for (String name:actual) { if (expected.contains(name)) tp++; else fp++; }
            for (String name:expected) if (!actual.contains(name)) fn++;
        }

        List<AIAnalysis> succeeded=analyses.findAll().stream()
                .filter(a -> "SUCCEEDED".equals(a.analysisStatus())).toList();
        Double avgCalls=succeeded.isEmpty()?null:succeeded.stream().mapToInt(AIAnalysis::toolCallCount).average().orElse(0);
        Double avgDuration=succeeded.isEmpty()?null:succeeded.stream().mapToLong(AIAnalysis::durationMs).average().orElse(0);

        double[] prf=planSamples>0?prf(tp,fp,fn):new double[]{-1,-1,-1};
        return new AgentEvaluation(total,
                total>0?(double)success/total:null,
                total>0?(double)(total-rejected)/total:null,
                prf[0]<0?null:prf[0],prf[1]<0?null:prf[1],prf[2]<0?null:prf[2],
                avgCalls,avgDuration);
    }

    /** 采纳/修正需要 AI 结果与人工审核配对；效率指标需要真实计时数据，本期未测量。 */
    private BusinessEvaluation businessEvaluation() {
        List<ReviewRecord> paired=reviews.findAll().stream()
                .filter(r -> r.analysisId()!=null && r.aiVerdict()!=null).toList();
        int samples=paired.size();
        long adopted=paired.stream().filter(r -> expectedReview(r.aiVerdict()).equals(r.reviewResult())).count();
        return new BusinessEvaluation(samples,
                samples>0?(double)adopted/samples:null,
                samples>0?(double)(samples-adopted)/samples:null,
                null,null,null,"NOT_MEASURED");
    }

    private static String expectedReview(String aiVerdict) {
        return switch (aiVerdict) {
            case "SUSPICIOUS" -> "CONFIRMED_THREAT";
            case "LIKELY_BENIGN" -> "FALSE_POSITIVE";
            default -> "INVESTIGATING";
        };
    }

    private AIAnalysis latestSucceeded(String incidentId) {
        return analyses.findByIncident(incidentId).stream()
                .filter(a -> "SUCCEEDED".equals(a.analysisStatus())).reduce((a,b) -> b).orElse(null);
    }

    /** 返回 [precision, recall, f1]；分母为 0 的项以 -1 表示不可用，由调用方转为 null。 */
    private static double[] prf(long tp,long fp,long fn) {
        double p=(tp+fp)>0?(double)tp/(tp+fp):-1;
        double r=(tp+fn)>0?(double)tp/(tp+fn):-1;
        double f=(p>=0 && r>=0 && (p+r)>0)?2*p*r/(p+r):((tp+fp+fn)==0?-1:0);
        return new double[]{p,r,f};
    }
}
