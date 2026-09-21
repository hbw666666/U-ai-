package org.unipus.unipus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.unipus.exceptions.NetworkException;
import org.unipus.exceptions.UnknownQuestionTypeException;
import org.unipus.ui.TaskManagerPanel;
import org.unipus.unipus.Answer;
import org.unipus.unipus.CourseDetail;
import org.unipus.unipus.Task;
import org.unipus.unipus.TaskManager;
import org.unipus.unipus.UnitTaskSituation;
import org.unipus.util.JSONParsing;
import org.unipus.util.StringProcesser;
import org.unipus.util.WebUtils;
import org.unipus.web.UnipusRequest;
import org.unipus.web.response.AllTaskofCourseResponse;
import org.unipus.web.response.AnswerResponse;
import org.unipus.web.response.CourseListResponse;
import org.unipus.web.response.CourseResourceInfoByIdResponse;
import org.unipus.web.response.RequiredPartofCourseResponse;
import org.unipus.web.response.SubmitResponse;
import org.unipus.web.response.TaskInfoResponse;
import org.unipus.web.response.TotalAndUnitSituationResponse;

public class Learn {
    private static final Logger logger = LogManager.getLogger(Learn.class);
    UnipusRequest request;
    private String courseName;
    private long resourceId;
    private String courseResourceId;
    private String courseInstanceId;
    private int strategyId;
    private String courseResourceName;
    private CourseDetail course;
    private HashMap<String, List<String>> requiredTasks;
    private TotalAndUnitSituationResponse totalProgress;
    private UnitTaskSituation unitProgress;
    private final int MAX_SUBMIT_PER_MINUTE = 5;
    private final LinkedList<Long> submitTimestamps = new LinkedList();

    public boolean startLearn(Task task, CourseListResponse.CourseResource resource) {
        SubmitThrottle.ensureConfigTemplate();
        logger.info("Start learning.");
        if (!this.checkpoint(task)) {
            return false;
        }
        task.setProcessDescription("\u6b63\u5728\u83b7\u53d6\u6559\u7a0b\u4fe1\u606f");
        this.request = task.getRequest();
        if (!this.checkpoint(task)) {
            return false;
        }
        Response courseResourceInfo = this.request.getCourseResourceInfoById(String.valueOf(resource.getId()));
        if (task.isStopRequested()) {
            return false;
        }
        if (courseResourceInfo == null || !courseResourceInfo.isSuccessful()) {
            logger.error("Failed to get course resource info.");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u6559\u7a0b\u4fe1\u606f\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get course info failed: Please try again later.");
        }
        CourseResourceInfoByIdResponse courseInfo = JSONParsing.parseRequest(courseResourceInfo, CourseResourceInfoByIdResponse.class);
        if (!this.checkpoint(task)) {
            return false;
        }
        if (courseInfo == null || !courseInfo.isSuccess()) {
            logger.error("Failed to get course resource info.");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u6559\u7a0b\u4fe1\u606f\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get course info failed: Please try again later.");
        }
        this.courseName = courseInfo.getValue().getCourseResource().getCourseName();
        this.resourceId = courseInfo.getValue().getCourseResource().getCourseResourceId();
        this.courseResourceId = courseInfo.getValue().getCourseResource().getResourceId();
        this.courseInstanceId = courseInfo.getValue().getCourseResource().getCourseInstanceId();
        this.strategyId = courseInfo.getValue().getCourseResource().getStrategyId();
        this.courseResourceName = courseInfo.getValue().getCourseResource().getTutorial().getResourceName();
        if (!this.checkpoint(task)) {
            return false;
        }
        task.setProcessDescription("\u6b63\u5728\u83b7\u53d6\u6240\u6709\u9898\u76ee\u4fe1\u606f");
        Response allTaskInfo = this.request.getAllTasksofCourse(resource.getInstanceId());
        if (!this.checkpoint(task)) {
            return false;
        }
        if (allTaskInfo == null || !allTaskInfo.isSuccessful()) {
            logger.error("Failed to get course detail info.");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u6559\u7a0b\u4fe1\u606f\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get course info failed: Please try again later.");
        }
        AllTaskofCourseResponse courseDetailInfo = JSONParsing.parseRequest(allTaskInfo, AllTaskofCourseResponse.class);
        if (!this.checkpoint(task)) {
            return false;
        }
        if (courseDetailInfo == null || courseDetailInfo.getCode() != 0) {
            logger.error("Failed to get all task info of course.");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u6559\u7a0b\u4fe1\u606f\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get course info failed: Please try again later.");
        }
        this.course = CourseDetail.valueOf(courseDetailInfo.getCourse());
        if (!this.checkpoint(task)) {
            return false;
        }
        task.setProcessDescription("\u6b63\u5728\u83b7\u53d6\u5fc5\u4fee\u8bfe\u7a0b\u4fe1\u606f");
        Response requiredPartofCourse = this.request.getRequiredPartofCourse(resource.getStrategyId(), String.valueOf(this.resourceId));
        if (!this.checkpoint(task)) {
            return false;
        }
        if (requiredPartofCourse == null || !requiredPartofCourse.isSuccessful()) {
            logger.error("Failed to get required part of course.");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u5fc5\u4fee\u8bfe\u7a0b\u4fe1\u606f\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get required task id failed: Please try again later.");
        }
        RequiredPartofCourseResponse requiredTasksDetail = JSONParsing.parseRequest(requiredPartofCourse, RequiredPartofCourseResponse.class);
        if (!this.checkpoint(task)) {
            return false;
        }
        if (requiredTasksDetail == null || !requiredTasksDetail.isSuccess()) {
            logger.error("Failed to get all task info of course.");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u5fc5\u4fee\u8bfe\u7a0b\u4fe1\u606f\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get required task id failed: Please try again later.");
        }
        this.requiredTasks = requiredTasksDetail.getAllRequiredTasksinMap();
        if (!this.checkpoint(task)) {
            return false;
        }
        task.setProcessDescription("\u6b63\u5728\u67e5\u8be2\u5b66\u4e60\u8fdb\u5ea6");
        Response totalAndUnitSituation = this.request.getTotalAndUnitSituation(this.resourceId, task.getUser().getAppUserId());
        if (!this.checkpoint(task)) {
            return false;
        }
        if (totalAndUnitSituation == null || !totalAndUnitSituation.isSuccessful()) {
            logger.error("Failed to obtain the learning progress of the unit");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u5b66\u4e60\u8fdb\u5ea6\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get learning progress id failed: Please try again later.");
        }
        TotalAndUnitSituationResponse totalAndUnitSituationResponse = JSONParsing.parseRequest(totalAndUnitSituation, TotalAndUnitSituationResponse.class);
        if (!this.checkpoint(task)) {
            return false;
        }
        if (totalAndUnitSituationResponse == null || !totalAndUnitSituationResponse.isSuccess()) {
            logger.error("Failed to obtain the learning progress of the unit");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u5b66\u4e60\u8fdb\u5ea6\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get learning progress id failed: Please try again later\u3002");
        }
        this.totalProgress = totalAndUnitSituationResponse;
        if (!this.checkpoint(task)) {
            return false;
        }
        if (this.totalProgress.getValue().getTotalDetail().getFinishProgress() == 100.0) {
            logger.info("All units have been completed.");
            task.setProcessDescription("\u6240\u6709\u5355\u5143\u5df2\u5b8c\u6210\u5b66\u4e60");
            task.setStatus(Task.Status.COMPLETED);
            return true;
        }
        for (TotalAndUnitSituationResponse.Unit unit : this.totalProgress.getValue().getUnitList()) {
            if (task.isStopRequested()) {
                return false;
            }
            task.setProcessDescription("\u6b63\u5728\u5b66\u4e60" + unit.getCaption() + " : " + unit.getName());
            if (unit.getFinishProgress() == 100.0) {
                logger.info("{} : {} has been completed.", (Object)unit.getCaption(), (Object)unit.getName());
                task.setProcessDescription(unit.getCaption() + " : " + unit.getName() + "\u5df2\u5b8c\u6210");
                continue;
            }
            if (task.isStopRequested()) {
                return false;
            }
            if (this.learnUnit(task, unit)) {
                task.setProcessDescription(unit.getCaption() + " : " + unit.getName() + "\u5df2\u5b8c\u6210");
                continue;
            }
            if (task.isStopRequested()) {
                return false;
            }
            task.setProcessDescription("\u5355\u5143 " + unit.getCaption() + " : " + unit.getName() + " \u5df2\u8df3\u8fc7: \u65e0\u5fc5\u4fee\u8bfe\u7a0b\u6216\u5747\u5df2\u5b8c\u6210");
            logger.info("All task completed unit: {} : {}, skipped.", (Object)unit.getCaption(), (Object)unit.getName());
        }
        return true;
    }

    private boolean learnUnit(Task task, TotalAndUnitSituationResponse.Unit unit) {
        if (!this.checkpoint(task)) {
            return false;
        }
        task.setProcessDescription("\u6b63\u5728\u5b66\u4e60" + unit.getCaption() + " : " + unit.getName());
        Response courseTimeResponse = this.request.getTaskTimeInfo(this.courseInstanceId, task.getUser().getOpenId(), unit.getNodeId());
        try (Response response = courseTimeResponse;){
            if (courseTimeResponse == null || !courseTimeResponse.isSuccessful()) {
                throw new IOException();
            }
            this.course.initTaskTimes(courseTimeResponse.body().string());
        }
        catch (IOException e) {
            logger.error("Failed to get the time info of tasks");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u4efb\u52a1\u5f00\u59cb\u7ed3\u675f\u65f6\u95f4\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get time info of tasks failed: Please try again later.");
        }
        Response unitTaskSituationResponse = this.request.getUnitTaskSituation(unit.getNodeId(), this.resourceId, task.getUser().getAppUserId(), task.getUser().getSsoId());
        if (!this.checkpoint(task)) {
            return false;
        }
        if (unitTaskSituationResponse == null || !unitTaskSituationResponse.isSuccessful()) {
            logger.error("Failed to get the learning progress of the task");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u5355\u5143\u5b66\u4e60\u8fdb\u5ea6\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get unit learning progress id failed: Please try again later.");
        }
        this.unitProgress = UnitTaskSituation.parse(unitTaskSituationResponse);
        if (!this.checkpoint(task)) {
            return false;
        }
        if (this.unitProgress == null) {
            logger.error("Failed to get the learning progress of the task");
            task.setStatus(Task.Status.ERROR);
            task.setProcessDescription("\u83b7\u53d6\u5355\u5143\u5b66\u4e60\u8fdb\u5ea6\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
            throw new NetworkException("Get unit learning progress id failed: Please try again later.");
        }
        if (!this.checkpoint(task)) {
            return false;
        }
        List<String> unitRequiredTasks = this.requiredTasks.get(unit.getNodeId());
        if (unitRequiredTasks == null || unitRequiredTasks.isEmpty()) {
            return false;
        }
        if (!this.checkpoint(task)) {
            return false;
        }
        List<String> needLearnTasks = unitRequiredTasks.stream().filter(taskId -> this.unitProgress.getNodeByNodeId((String)taskId).getFinishProgress() != 100.0).toList();
        if (needLearnTasks.isEmpty()) {
            return false;
        }
        for (String taskId2 : needLearnTasks) {
            task.setProcessDescription("\u6b63\u5728\u5b66\u4e60\u4efb\u52a1 " + unit.getCaption() + " : " + unit.getName() + " - " + this.course.getNode(taskId2).getName());
            if (this.learnTask(task, taskId2)) continue;
            if (task.isStopRequested()) {
                return false;
            }
            task.setProcessDescription("\u4e0d\u652f\u6301\u7684\u4efb\u52a1 " + unit.getCaption() + " : " + unit.getName() + " - " + this.course.getNode(taskId2).getName());
        }
        return true;
    }

    private boolean learnTask(Task task, String taskId) {
        List<CourseDetail.Node.BaseType> questionTypes;
        if (!this.checkpoint(task)) {
            return false;
        }
        ArrayList<Long> ids = new ArrayList<Long>();
        ArrayList<List<String>> answers = new ArrayList<List<String>>();
        long now = System.currentTimeMillis() / 1000L;
        long taskStartTime = this.course.getTaskStartTime(taskId);
        long taskEndTime = this.course.getTaskEndTime(taskId);
        if (taskStartTime != 0L && taskEndTime != 0L && (taskStartTime >= now || now >= taskEndTime)) {
            logger.info("Task {} has not started yet, skipped.", (Object)taskId);
            return false;
        }
        try {
            questionTypes = this.course.getQuestionTypes(taskId);
        }
        catch (UnknownQuestionTypeException e2) {
            logger.warn("Unknown question type of task {}, skipped.", (Object)taskId);
            return false;
        }
        if (questionTypes.size() != 1) {
            return false;
        }
        List<CourseDetail.Node.BaseType> typesNeedSkip = List.of(CourseDetail.Node.BaseType.DISCUSSION, CourseDetail.Node.BaseType.MULTI_FILE_UPLOAD, CourseDetail.Node.BaseType.EXIT_TICKET, CourseDetail.Node.BaseType.MULTICHOICE, CourseDetail.Node.BaseType.UNKNOWN);
        if (!Collections.disjoint(questionTypes, typesNeedSkip)) {
            logger.info("Unsupported question type of task {} : {}, skipped.", (Object)taskId, (Object)Arrays.toString(questionTypes.toArray()));
            return false;
        }
        if (!new HashSet<CourseDetail.Node.BaseType>(CourseDetail.PRESET_MODES).containsAll(questionTypes)) {
            ArrayList<String> answers0 = new ArrayList<String>();
            if (!this.checkpoint(task)) {
                return false;
            }
            Response answerRes = this.request.getAnswer(this.courseInstanceId, taskId, task.getUser().getOpenId());
            if (!this.checkpoint(task)) {
                return false;
            }
            if (answerRes == null || !answerRes.isSuccessful()) {
                task.setStatus(Task.Status.ERROR);
                task.setProcessDescription("\u83b7\u53d6\u9898\u76ee\u7b54\u6848\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
                throw new NetworkException("Get answer of task " + taskId + " failed: Please try again later.");
            }
            AnswerResponse answerResponse = JSONParsing.parseRequest(answerRes, AnswerResponse.class);
            if (!this.checkpoint(task)) {
                return false;
            }
            if (answerResponse == null || answerResponse.getCode() != 0) {
                task.setStatus(Task.Status.ERROR);
                task.setProcessDescription("\u83b7\u53d6\u9898\u76ee\u7b54\u6848\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
                throw new NetworkException("Get answer of task " + taskId + " failed: Please try again later.");
            }
            String answerString = StringProcesser.decrypt(answerResponse.getData(), answerResponse.getK());
            logger.trace("Decrypted answer string of task {} : {}", (Object)taskId, (Object)answerString);
            if (!this.checkpoint(task)) {
                return false;
            }
            for (int i = 0; i < this.course.getNode(taskId).getQuestion_num(); ++i) {
                Answer answer = Answer.getInstanceByJSON(answerString, i);
                CourseDetail.Node.BaseType questionType = this.course.getQuestionType(taskId, i);
                try {
                    switch (questionType) {
                        case MATERIAL_BANKED_CLOZE: 
                        case SINGLE_CHOICE: 
                        case SEQUENCE: 
                        case BASIC_SCOOP_CONTENT: 
                        case TRANSLATION: 
                        case VIDEO_POPUP: {
                            answer.getQuestionAnswers().getChildren().forEach(e -> answers0.add(e.getAnswer().getFirst()));
                            break;
                        }
                        case SHORT_ANSWER: {
                            answer.getQuestionAnalysis().getChildren().forEach(e -> answers0.add(e.getAnalysis()));
                            break;
                        }
                        case WRITING: {
                            answers0.add(answer.getQuestionAnalysis().getAnalysis());
                            break;
                        }
                        case RICH_TEXT_READ: 
                        case TEXT_LEARN: 
                        case VIDEO_POINT_READ: 
                        case VOCABULARY: 
                        case INPUT: {
                            break;
                        }
                        default: {
                            throw new IllegalStateException("Unexpected enum value: " + String.valueOf((Object)questionType));
                        }
                    }
                }
                catch (NullPointerException e3) {
                    logger.error("Something wrong when getting answer of task {} : {}, skipped, and you should report this to developer.", (Object)taskId, (Object)e3.getMessage());
                }
                answers.add(answers0);
                ids.add(answer.getId());
            }
        } else {
            for (int i = 0; i < this.course.getNode(taskId).getQuestion_num(); ++i) {
                answers.add(new ArrayList());
                ids.add(0L);
            }
        }
        String submitBody = WebUtils.createSubmitBody(ids, answers, taskId, this.courseInstanceId, task.getUser().getOpenId(), questionTypes);
        boolean submitSuccess = false;
        do {
            if (!this.checkpoint(task)) {
                return false;
            }
            long now2 = System.currentTimeMillis();
            while (!this.submitTimestamps.isEmpty() && this.submitTimestamps.peek() + 60000L < now2) {
                this.submitTimestamps.poll();
            }
            if (!this.checkpoint(task)) {
                return false;
            }
            if (this.submitTimestamps.size() >= 5) {
                long waitMs = Math.max(0L, this.submitTimestamps.peek() + 60000L - now2);
                logger.info("Submit too frequently, wait {} ms", (Object)waitMs);
                task.waitForCooldown(waitMs, "\u63d0\u4ea4\u901f\u5ea6\u592a\u5feb\u4e86\uff0c\u4f11\u606f\u4e00\u4e0b", "\u7ee7\u7eed\u63d0\u4ea4");
                if (!this.checkpoint(task)) {
                    return false;
                }
            }
            // 提交节流：等待一个最小间隔。原版没有任何提交间隔，
            // 连续提交会被服务端判定“提交速度太快”（600001 / 600002）。
            long throttleWait = SubmitThrottle.delayBeforeSubmit();
            if (throttleWait > 0L) {
                task.waitForCooldown(throttleWait, "提交速度太快了，正在按节流间隔等待", "继续提交");
                if (!this.checkpoint(task)) {
                    return false;
                }
            }
            Response submit = this.request.submit(submitBody, task.getUser().getOpenId());
            if (!this.checkpoint(task)) {
                return false;
            }
            SubmitThrottle.recordSubmit();
            if (submit == null || !submit.isSuccessful()) {
                task.setStatus(Task.Status.ERROR);
                task.setProcessDescription("\u63d0\u4ea4\u7b54\u6848\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
                throw new NetworkException("Get answer of task " + taskId + " failed: Please try again later\u3002");
            }
            SubmitResponse submitResponse = JSONParsing.parseRequest(submit, SubmitResponse.class);
            if (!this.checkpoint(task)) {
                return false;
            }
            if (submitResponse == null || submitResponse.getCode() != 0 && submitResponse.getCode() != 600001 && submitResponse.getCode() != 600002) {
                task.setStatus(Task.Status.ERROR);
                task.setProcessDescription("\u63d0\u4ea4\u7b54\u6848\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u7f51\u7edc\u8fde\u63a5\u7136\u540e\u91cd\u8bd5");
                throw new NetworkException("Get answer of task " + taskId + " failed: Please try again later\u3002");
            }
            if (!this.checkpoint(task)) {
                return false;
            }
            if (submitResponse.getCode() == 600001 || submitResponse.getCode() == 600002) {
                // 服务端明确限流：使用指数退避，连续触发等待时间逐次翻倍
                SubmitThrottle.onRateLimited();
                long penalty = SubmitThrottle.delayBeforeSubmit();
                logger.info("Submitting too frequently, wait {} ms.", (Object) penalty);
                task.waitForCooldown(Math.max(penalty, 120000L), "\u63d0\u4ea4\u901f\u5ea6\u592a\u5feb\u4e86\uff0c\u4f11\u606f\u4e24\u5206\u949f", "\u7ee7\u7eed\u63d0\u4ea4");
                continue;
            }
            SubmitThrottle.onAccepted();
            String version = submitResponse.getData().getVersion();
            boolean skipped = false;
            Response taskInfo = this.request.getTaskInfo(this.courseInstanceId, taskId, version, task.getUser().getOpenId());
            if (!this.checkpoint(task)) {
                return false;
            }
            if (taskInfo == null || !taskInfo.isSuccessful()) {
                task.setProcessDescription("\u83b7\u53d6\u9898\u76ee\u4fe1\u606f\u5931\u8d25");
                logger.warn("Get task info " + taskId + " failed, skipped check 0 score.");
                skipped = true;
            }
            TaskInfoResponse taskInfoResponse = JSONParsing.parseRequest(taskInfo, TaskInfoResponse.class);
            if (!this.checkpoint(task)) {
                return false;
            }
            if (taskInfoResponse == null || taskInfoResponse.getCode() != 0) {
                task.setProcessDescription("\u83b7\u53d6\u9898\u76ee\u4fe1\u606f\u5931\u8d25");
                logger.warn("Get task info " + taskId + " failed, skipped check 0 score.");
                skipped = true;
            }
            if (!skipped) {
                AtomicBoolean counted = new AtomicBoolean(false);
                taskInfoResponse.getData().getState().getExtendData().getSummary().getAnswerList().forEach((e, a) -> {
                    if (a.getQuestionType() == 1 || a.getQuestionType() == 3) {
                        counted.set(true);
                    }
                });
                if (counted.get() && submitResponse.getData().getState().getScoreAvg() == 0.0) {
                    logger.warn("The score of task {} is 0, please check it later.", (Object)taskId);
                    task.setProcessDescription("\u6ce8\u610f\uff1a\u4efb\u52a1 " + this.course.getNode(taskId).getName() + " \u7684\u5f97\u5206\u4e3a0\uff0c\u5df2\u81ea\u52a8\u6682\u505c");
                    task.suspendTask();
                    TaskManagerPanel.getInstance().warnPopup("\u4efb\u52a1 " + this.course.getNode(taskId).getName() + " \u76ee\u524d\u7684\u5f97\u5206\u4e3a0\uff0c\u4e3a\u9632\u6b62\u4e8b\u4ef6\u518d\u6b21\u53d1\u751f\uff0cTask\u5df2\u81ea\u52a8\u6682\u505c\uff0c\u60a8\u53ef\u80fd\u9700\u8981\u6392\u67e5\u60c5\u51b5\u3002\n\u53ef\u80fd\u7684\u539f\u56e0\u5305\u62ec\u4f46\u4e0d\u9650\u4e8e\uff1a\n1. \u672a\u77e5\u7684\u65b0\u9898\u578b\uff0c\u7a0b\u5e8f\u7684\u63d0\u4ea4\u903b\u8f91\u6709\u95ee\u9898\u3002\n2. U\u6821\u56ed\u670d\u52a1\u7aef\u7ed9\u5b9a\u7684\u7b54\u6848\u6216\u903b\u8f91\u6709\u8bef\n3. \u5176\u4ed6\u672a\u77e5\u539f\u56e0\n\n\u60a8\u73b0\u5728\u53ef\u4ee5\u5f00\u59cb\u6392\u67e5\uff1a\n1. \u5728U\u6821\u56ed\u4e2d\u627e\u5230\u8fd9\u4e2a\u9898\u76ee\u5e76\u67e5\u770b\u8fd9\u4e2a\u9898\u76ee\u662f\u5426\u552f\u4e00\uff0c\u82e5\u53ea\u6709\u8fd9\u4e2a\u9898\u548c\u5176\u4ed6\u9898\u76ee\u4e0d\u4e00\u6837\uff0c\u53ef\u4ee5\u7ee7\u7eed\u6062\u590d\u8fd0\u884c\n2. \u82e5\u8fd9\u4e2a\u9898\u76ee\u7c7b\u578b\u975e\u552f\u4e00\uff0c\u6216\u60a8\u5df2\u786e\u8ba4\u8fd9\u4e2a\u662f\u4e0d\u652f\u6301\u7684\u65b0\u9898\u578b\uff0c\u53ef\u4ee5\u5728github issue\u9875\u9762\u53cd\u9988\u5f00\u53d1\u8005\n\n\u6b63\u5728\u8fdb\u884c\u7684\u9898\u76eeid\uff1a" + taskId);
                }
            }
            submitSuccess = true;
        } while (!submitSuccess);
        if (!this.checkpoint(task)) {
            return false;
        }
        Response courseListResp = this.request.getCourseList();
        if (courseListResp == null || !courseListResp.isSuccessful()) {
            logger.warn("Failed to update course progress because of network issues.");
            return true;
        }
        if (!this.checkpoint(task)) {
            return false;
        }
        CourseListResponse courseListResponse = JSONParsing.parseRequest(courseListResp, CourseListResponse.class);
        if (courseListResponse == null || !courseListResponse.isSuccess()) {
            logger.warn("Failed to update course progress because of network issues.");
            return true;
        }
        boolean progressUpdated = false;
        block14: for (CourseListResponse.Course course : courseListResponse.getValue().getCourseList()) {
            for (CourseListResponse.CourseResource courseResource : course.getCourseResourceList()) {
                if (!courseResource.equals(task.getCurrentCourseResource())) continue;
                TaskManager.getInstance().updateTaskProgress(task.getTaskId(), courseResource.getFinishPointNum(), courseResource.getTotalPointNum());
                logger.debug("Updated task progress: {}/{}", (Object)courseResource.getFinishPointNum(), (Object)courseResource.getTotalPointNum());
                progressUpdated = true;
                break block14;
            }
        }
        if (!progressUpdated) {
            logger.warn("Did not find matching CourseResource to update progress for task {} (instanceId={}), UI may not refresh.", (Object)task.getTaskId(), (Object)(task.getCurrentCourseResource() != null ? task.getCurrentCourseResource().getInstanceId() : "null"));
        }
        return true;
    }

    private boolean checkpoint(Task task) {
        if (task.isStopRequested()) {
            return false;
        }
        if (task.getStatus().equals((Object)Task.Status.PAUSED)) {
            task.suspendTask();
        }
        return true;
    }
}

