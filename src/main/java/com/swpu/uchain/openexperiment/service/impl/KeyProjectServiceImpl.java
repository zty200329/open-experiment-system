package com.swpu.uchain.openexperiment.service.impl;

import com.sun.org.apache.xpath.internal.operations.Operation;
import com.swpu.uchain.openexperiment.DTO.KeyProjectDTO;
import com.swpu.uchain.openexperiment.DTO.KeyProjectDTO1;
import com.swpu.uchain.openexperiment.DTO.OperationRecord;
import com.swpu.uchain.openexperiment.DTO.ProjectHistoryInfo;
import com.swpu.uchain.openexperiment.VO.limit.AmountAndTypeVO;
import com.swpu.uchain.openexperiment.VO.limit.AmountLimitVO;
import com.swpu.uchain.openexperiment.VO.project.CheckProjectVO;
import com.swpu.uchain.openexperiment.VO.project.CheckProjectVO1;
import com.swpu.uchain.openexperiment.VO.project.ProjectReviewVO;
import com.swpu.uchain.openexperiment.VO.user.UserMemberVO;
import com.swpu.uchain.openexperiment.accessctro.ExcelResources;
import com.swpu.uchain.openexperiment.domain.*;
import com.swpu.uchain.openexperiment.form.amount.AmountAndType;
import com.swpu.uchain.openexperiment.form.project.*;
import com.swpu.uchain.openexperiment.mapper.*;
import com.swpu.uchain.openexperiment.enums.*;
import com.swpu.uchain.openexperiment.exception.GlobalException;
import com.swpu.uchain.openexperiment.form.query.HistoryQueryKeyProjectInfo;
import com.swpu.uchain.openexperiment.form.check.KeyProjectCheck;
import com.swpu.uchain.openexperiment.form.query.QueryConditionForm;
import com.swpu.uchain.openexperiment.form.user.StuMember;
import com.swpu.uchain.openexperiment.result.Result;
import com.swpu.uchain.openexperiment.service.*;
import com.swpu.uchain.openexperiment.util.SerialNumberUtil;
import com.swpu.uchain.openexperiment.util.SortListUtil;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.sf.jsqlparser.statement.select.Join;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sun.rmi.runtime.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * @author dengg
 */
@Slf4j
@Service
public class KeyProjectServiceImpl implements KeyProjectService {

    private UserProjectAccountMapper userProjectAccountMapper;
    private ProjectGroupMapper projectGroupMapper;
    private UserProjectGroupMapper userProjectGroupMapper;
    private KeyProjectStatusMapper keyProjectStatusMapper;
    private GetUserService getUserService;
    private OperationRecordMapper operationRecordMapper;
    private TimeLimitService timeLimitService;
    private AmountLimitMapper amountLimitMapper;
    private ProjectFileMapper projectFileMapper;
    private UserRoleService userRoleService;
    private HitBackMessageMapper hitBackMessageMapper;
    private AchievementMapper achievementMapper;
    private UserProjectService userProjectService;
    private CollegeGivesGradeMapper collegeGivesGradeMapper;
    private FunctionGivesGradeMapper functionGivesGradeMapper;
    private CollegeLimitMapper collegeLimitMapper;
    private AmountLimitService amountLimitService;
    private ProjectReviewMapper projectReviewMapper;
    private ProjectReviewResultMapper projectReviewResultMapper;
    private MaxBigFundsMapper maxBigFundsMapper;
    @Autowired
    public KeyProjectServiceImpl(ProjectGroupMapper projectGroupMapper, UserProjectGroupMapper userProjectGroupMapper,
                                 KeyProjectStatusMapper keyProjectStatusMapper,GetUserService getUserService,
                                 OperationRecordMapper operationRecordMapper,TimeLimitService timeLimitService,
                                 AmountLimitMapper amountLimitMapper,ProjectFileMapper projectFileMapper,
                                 UserRoleService userRoleService,HitBackMessageMapper hitBackMessageMapper,
                                 AchievementMapper achievementMapper,UserProjectService userProjectService,
                                 CollegeGivesGradeMapper collegeGivesGradeMapper,FunctionGivesGradeMapper functionGivesGradeMapper,CollegeLimitMapper collegeLimitMapper,
                                 AmountLimitService amountLimitService,ProjectReviewMapper projectReviewMapper,
                                 ProjectReviewResultMapper projectReviewResultMapper,MaxBigFundsMapper maxBigFundsMapper,
                                 UserProjectAccountMapper userProjectAccountMapper) {
        this.projectGroupMapper = projectGroupMapper;
        this.userProjectGroupMapper = userProjectGroupMapper;
        this.keyProjectStatusMapper = keyProjectStatusMapper;
        this.getUserService = getUserService;
        this.operationRecordMapper = operationRecordMapper;
        this.timeLimitService = timeLimitService;
        this.amountLimitMapper = amountLimitMapper;
        this.projectFileMapper = projectFileMapper;
        this.userRoleService = userRoleService;
        this.hitBackMessageMapper = hitBackMessageMapper;
        this.achievementMapper=achievementMapper;
        this.userProjectGroupMapper=userProjectGroupMapper;
        this.collegeGivesGradeMapper=collegeGivesGradeMapper;
        this.functionGivesGradeMapper=functionGivesGradeMapper;
        this.collegeLimitMapper=collegeLimitMapper;
        this.amountLimitService=amountLimitService;
        this.projectReviewMapper=projectReviewMapper;
        this.projectReviewResultMapper = projectReviewResultMapper;
        this.userProjectAccountMapper = userProjectAccountMapper;
        this.maxBigFundsMapper = maxBigFundsMapper;
    }


    /**
     * ??????????????????
     * @param form ????????????
     * @return
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public Result createKeyApply(KeyProjectApplyForm form) {

        //?????????????????????
        User user = getUserService.getCurrentUser();
        if (user.getMobilePhone() == null) {
            throw new GlobalException(CodeMsg.USER_INFO_NOT_COMPLETE);
        }

        //????????????????????????
        ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(form.getProjectId());
        if (projectGroup == null){
            throw new GlobalException(CodeMsg.PROJECT_GROUP_NOT_EXIST);
        }

        //?????????????????????????????????
        if (ProjectType.GENERAL.getValue().equals(projectGroup.getProjectType())){
            throw new GlobalException(CodeMsg.GENERAL_PROJECT_CANT_APPLY);
        }

        //???????????????????????????????????????????????????????????????????????????
        ProjectFile projectFile = projectFileMapper.selectByProjectGroupIdAndMaterialType(form.getProjectId(),MaterialType.APPLY_MATERIAL.getValue(),null);
        if (projectFile == null) {
            throw new GlobalException(CodeMsg.KEY_PROJECT_APPLY_MATERIAL_EMPTY);
        }

        //?????????????????????????????????????????????
        UserProjectGroup userProjectGroup = userProjectGroupMapper.selectByProjectGroupIdAndUserId(form.getProjectId(), Long.valueOf(user.getCode()));
        if (userProjectGroup == null || !userProjectGroup.getMemberRole().equals(MemberRole.PROJECT_GROUP_LEADER.getValue())){
            throw new GlobalException(CodeMsg.PERMISSION_DENNY);
        }

        //????????????????????????
        timeLimitService.validTime(TimeLimitType.KEY_DECLARE_LIMIT);

        //???????????????????????????
        Long projectId = projectGroup.getId();
        if (!projectGroup.getStatus().equals(ProjectStatus.LAB_ALLOWED.getValue()) &&
                //?????????????????????????????????????????????????????????
            !projectGroup.getStatus().equals(ProjectStatus.REJECT_MODIFY.getValue()) &&

                //??????????????????????????????????????????????????????????????????????????????????????????????????????
            !keyProjectStatusMapper.getStatusByProjectId(projectId).equals(ProjectStatus.TO_DE_CONFIRMED.getValue()
        )){
            throw new GlobalException(CodeMsg.PROJECT_IS_NOT_LAB_ALLOWED);
        }
        //??????????????????????????????????????????????????????????????????????????????????????????????????????
        Integer keyProjectStatus = keyProjectStatusMapper.getStatusByProjectId(projectGroup.getId());
        if (keyProjectStatus !=null && !keyProjectStatus.equals(ProjectStatus.REJECT_MODIFY.getValue())) {
            throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
        }
        //?????????????????????????????????????????????????????????  ???????????????????????????????????????????????????????????????
        projectGroupMapper.updateProjectStatus(form.getProjectId(),ProjectStatus.KEY_PROJECT_APPLY.getValue());

        if (keyProjectStatus == null) {
            //??????????????????
            keyProjectStatusMapper.insert(projectId, ProjectStatus.TO_DE_CONFIRMED.getValue(),
                    projectGroup.getSubordinateCollege(), Long.valueOf(user.getCode()));
        }else {
            //???????????????
            keyProjectStatusMapper.update(form.getProjectId(),ProjectStatus.TO_DE_CONFIRMED.getValue());
        }


        //??????????????????????????????????????????
        OperationRecord operationRecord = new OperationRecord();
        operationRecord.setOperationUnit(RoleType.PROJECT_LEADER.getValue());
        operationRecord.setOperationType(OperationType.REPORT.getValue());
        operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));
        operationRecord.setRelatedId(projectGroup.getId());

        List<StuMember> stuMemberList = form.getMembers();

        if (stuMemberList!=null){
            for (StuMember stuMember:stuMemberList
            ) {
                userProjectGroupMapper.updateUserInfo(stuMember, new Date(), projectId);
            }
        }

        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteKeyProject(List<KeyProjectCheck> list) {
        for (KeyProjectCheck keyProjectCheck : list) {
            Integer keyProjectStatus = keyProjectStatusMapper.getStatusByProjectId(keyProjectCheck.getProjectId());

            List<UserProjectGroup> userProjectGroups = userProjectGroupMapper.selectByProjectGroupId(keyProjectCheck.getProjectId());
            log.info(userProjectGroups.toString());
            for (UserProjectGroup group : userProjectGroups) {
                //??????????????????
                UserProjectAccount userProjectAccount2 = userProjectAccountMapper.selectByCode(String.valueOf(group.getUserId()));
                //?????????????????????
                if(userProjectAccount2 != null) {

                        userProjectAccount2.setKeyNum(userProjectAccount2.getKeyNum() - 1);

                    userProjectAccountMapper.updateByPrimaryKey(userProjectAccount2);
                }
            }
            if (keyProjectStatus == null) {
                //??????????????????????????????
                projectGroupMapper.deleteByPrimaryKey(keyProjectCheck.getProjectId());
                userProjectGroupMapper.deleteByProjectGroupId(keyProjectCheck.getProjectId());
                operationRecordMapper.deleteByGroupId(keyProjectCheck.getProjectId());
            }else {

                if (!(keyProjectStatus >= -4 && keyProjectStatus <= 2 && keyProjectStatus != -3)) {
                    return Result.error(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
                }

                projectGroupMapper.deleteByPrimaryKey(keyProjectCheck.getProjectId());
                userProjectGroupMapper.deleteByProjectGroupId(keyProjectCheck.getProjectId());
                operationRecordMapper.deleteByGroupId(keyProjectCheck.getProjectId());
                keyProjectStatusMapper.deleteByProjectId(keyProjectCheck.getProjectId());
            }
        }
        return Result.success();
    }

    @Override
    public Result getKeyProjectApplyingListByGuideTeacher() {
        User user = getUserService.getCurrentUser();
        if (user == null){
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        Long userId = Long.valueOf(user.getCode());
        List<KeyProjectDTO> list = keyProjectStatusMapper.getKeyProjectListByUserIdAndProjectStatus(userId,ProjectStatus.TO_DE_CONFIRMED.getValue());
        for (KeyProjectDTO keyProjectDTO :list
        ) {
            keyProjectDTO.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(keyProjectDTO.getId(),null));
            keyProjectDTO.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),keyProjectDTO.getId(),JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }

    /**
     * ????????????
     * @param iconicResultForms
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result insertIconicResult(List<IconicResultForm> iconicResultForms) {
        User user = getUserService.getCurrentUser();
        if (user == null){
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        //TODO ????????????????????????
        Achievement achievement = new Achievement();
        for (IconicResultForm iconicResultForm : iconicResultForms) {
            log.info(achievement.toString());
            BeanUtils.copyProperties(iconicResultForm,achievement);
            achievement.setGmtCreate(new Date());
            achievement.setGmtModified(new Date());
            achievementMapper.insert(achievement);
        }
        return Result.success();
    }

    /**
     * ????????????
     * ?????????????????????????????????
     * @param id
     * @return
     */
    @Override
    public Result deleteIconicResult(Long id) {
        Achievement achievement = achievementMapper.selectByPrimaryKey(id);
        User currentUser = getUserService.getCurrentUser();
        if (currentUser == null){
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        log.info(achievement.toString());
        achievementMapper.deleteByPrimaryKey(id);
        return Result.success();
    }

    @Override
    public Result getKeyProjectApplyingListByLabAdmin() {
         User user  = getUserService.getCurrentUser();
         return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.GUIDE_TEACHER_ALLOWED,user.getInstitute());
    }


    /**
     * ????????????????????????????????????
     * @return
     */
    @Override
    public Result getKeyProjectAllListByLabAdmin() {
        User user  = getUserService.getCurrentUser();
        List<CheckProjectVO> list = keyProjectStatusMapper.getAllByCollege(user.getInstitute());
        List<CheckProjectVO1> list1 = new LinkedList<>();
        for (CheckProjectVO keyProjectDTO :list) {
            CheckProjectVO1 checkProjectVO1 = new CheckProjectVO1();
            BeanUtils.copyProperties(keyProjectDTO,checkProjectVO1);
            if(keyProjectStatusMapper.getStatusByProjectId(keyProjectDTO.getId()) != null){
                checkProjectVO1.setKeyStatus(keyProjectStatusMapper.getStatusByProjectId(keyProjectDTO.getId()));
            }
            checkProjectVO1.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(keyProjectDTO.getId(),JoinStatus.JOINED.getValue()) );
            checkProjectVO1.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),keyProjectDTO.getId(),JoinStatus.JOINED.getValue()));
            list1.add(checkProjectVO1);
        }
        return Result.success(list1);
    }

    /**
     * ????????????????????????????????????
     * @return
     */
    @Override
    public Result getKeyProjectAllListBySchool() {
        User user  = getUserService.getCurrentUser();
        List<CheckProjectVO> list = keyProjectStatusMapper.getAllByCollege(user.getInstitute());
        List<CheckProjectVO1> list1 = new LinkedList<>();
        for (CheckProjectVO keyProjectDTO :list) {
            CheckProjectVO1 checkProjectVO1 = new CheckProjectVO1();
            BeanUtils.copyProperties(keyProjectDTO,checkProjectVO1);
            if(keyProjectStatusMapper.getStatusByProjectId(keyProjectDTO.getId()) != null){
                checkProjectVO1.setKeyStatus(keyProjectStatusMapper.getStatusByProjectId(keyProjectDTO.getId()));
            }
            checkProjectVO1.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(keyProjectDTO.getId(),JoinStatus.JOINED.getValue()) );
            checkProjectVO1.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),keyProjectDTO.getId(),JoinStatus.JOINED.getValue()));
            list1.add(checkProjectVO1);
        }
        return Result.success(list1);
    }

    @Override
    public Result getKeyProjectApplyingListBySecondaryUnit() {
        User user  = getUserService.getCurrentUser();
        ProjectReview projectReview = projectReviewMapper.selectByCollegeAndType(user.getInstitute(),ProjectType.KEY.getValue());
        if(projectReview != null){
            //????????????
            return getReviewInfo2();
        }else {
            return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.LAB_ALLOWED_AND_REPORTED, user.getInstitute());
        }
    }

    @Override
    public Result getToBeReviewedProject() {
        User user  = getUserService.getCurrentUser();
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.PROJECT_REVIEW,user.getInstitute());
    }

    @Override
    public Result getCollegeKeyProject() {
        User user  = getUserService.getCurrentUser();
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.ESTABLISH,user.getInstitute());
    }

    @Override
    public Result getTheCollegeHasCompletedKeyProject() {
        User user  = getUserService.getCurrentUser();
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.COLLEGE_FINAL_SUBMISSION,user.getInstitute());
    }

    @Override
    public Result getTheSchoolHasCompletedKeyProject() {
        User user  = getUserService.getCurrentUser();
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.CONCLUDED,user.getInstitute());
    }

    @Override
    public Result getKeyProjectApplyingListByFunctionalDepartment() {
        User user  = getUserService.getCurrentUser();
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED,null);
    }

    /**
     * ????????????????????????????????????
     * @param college
     * @return
     */
    @Override
    public Result getIntermediateInspectionKeyProject(Integer college) {
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.ESTABLISH,college);
    }


    @Override
    public Result getToBeConcludingKeyProject(Integer college) {
        return getKeyProjectDTOListByStatusAndCollege2(ProjectStatus.COLLEGE_FINAL_SUBMISSION,college);
    }

    @Override
    public Result getCompleteKeyProject(Integer college) {
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.CONCLUDED,college);
    }

    private Result getKeyProjectDTOListByStatusAndCollege2(ProjectStatus status, Integer college){
        List<KeyProjectDTO> list = keyProjectStatusMapper.getKeyProjectDTOListByStatusAndCollege2(college);
        for (KeyProjectDTO keyProjectDTO :list) {
            keyProjectDTO.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(keyProjectDTO.getId(),JoinStatus.JOINED.getValue()) );
            keyProjectDTO.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),keyProjectDTO.getId(),JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }
    private Result getKeyProjectDTOListByStatusAndCollege(ProjectStatus status, Integer college){
        List<KeyProjectDTO> list = keyProjectStatusMapper.getKeyProjectDTOListByStatusAndCollege(status.getValue(),college);
        for (KeyProjectDTO keyProjectDTO :list) {
            keyProjectDTO.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(keyProjectDTO.getId(),JoinStatus.JOINED.getValue()) );
            keyProjectDTO.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),keyProjectDTO.getId(),JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }

    private ProjectStatus getNextStatusByRoleAndOperation(RoleType roleType, OperationType operationType){
        ProjectStatus keyProjectStatus;
        //??????????????????????????????????????? ??????????????????
        if (operationType == OperationType.REJECT) {
            if ((roleType == RoleType.SECONDARY_UNIT || roleType == RoleType.FUNCTIONAL_DEPARTMENT)) {
                keyProjectStatus = ProjectStatus.ESTABLISH_FAILED;
                return keyProjectStatus;
            }
        }

        //??????????????????????????????????????????????????????????????????
        if (operationType == OperationType.REPORT_REJECT ) {
            if (roleType == RoleType.SECONDARY_UNIT ||
                    roleType == RoleType.FUNCTIONAL_DEPARTMENT)  {
                return ProjectStatus.ESTABLISH_FAILED;
            }
        }

        //????????????????????????????????????
        if(operationType == OperationType.OFFLINE_CHECK_REJECT){
            if((roleType==RoleType.FUNCTIONAL_DEPARTMENT||roleType == RoleType.FUNCTIONAL_DEPARTMENT_LEADER)){
                return ProjectStatus.ESTABLISH_FAILED;
            }
        }
        if(operationType == OperationType.CONCLUSION_REJECT){
            if((roleType==RoleType.FUNCTIONAL_DEPARTMENT||roleType == RoleType.FUNCTIONAL_DEPARTMENT_LEADER
                    || roleType == RoleType.COLLEGE_FINALIZATION_REVIEW)){
                return ProjectStatus.ESTABLISH_FAILED;
            }
        }

        //????????????????????????
        if(operationType == OperationType.INTERIM_RETURN){
            if((roleType==RoleType.FUNCTIONAL_DEPARTMENT||roleType == RoleType.FUNCTIONAL_DEPARTMENT_LEADER)){
                return ProjectStatus.INTERIM_RETURN_MODIFICATION;
            }
        }
        //????????????????????????
        if(operationType == OperationType.FUNCTIONAL_ESTABLISH_PASSED){
            if((roleType==RoleType.FUNCTIONAL_DEPARTMENT||roleType == RoleType.FUNCTIONAL_DEPARTMENT_LEADER)){
                return ProjectStatus.ESTABLISH;
            }
        }
        //????????????????????????
        if(operationType == OperationType.FUNCTIONAL_ESTABLISH_RETURN){
            if((roleType==RoleType.FUNCTIONAL_DEPARTMENT||roleType == RoleType.FUNCTIONAL_DEPARTMENT_LEADER)){
                return ProjectStatus.FUNCTIONAL_ESTABLISH_RETURNS;
            }
        }
        if(operationType == OperationType.COLLEGE_RETURNS){
            if((roleType==RoleType.COLLEGE_FINALIZATION_REVIEW)){
                return ProjectStatus.COLLEGE_RETURNS;
            }
        }
        if(operationType == OperationType.FUNCTIONAL_RETURNS){
            if((roleType==RoleType.FUNCTIONAL_DEPARTMENT||roleType == RoleType.FUNCTIONAL_DEPARTMENT_LEADER)){
                return ProjectStatus.FUNCTIONAL_RETURNS;
            }
        }

        if(operationType == OperationType.COLLEGE_REVIEW_PASSED){
            if((roleType==RoleType.COLLEGE_FINALIZATION_REVIEW)){
                return ProjectStatus.COLLEGE_FINAL_SUBMISSION;
            }
        }
        if(operationType == OperationType.FUNCTIONAL_REVIEW_PASSED){
            if((roleType==RoleType.FUNCTIONAL_DEPARTMENT||roleType == RoleType.FUNCTIONAL_DEPARTMENT_LEADER)){
                return ProjectStatus.CONCLUDED;
            }
        }

        if(operationType == OperationType.COLLEGE_PASSED_THE_EXAMINATION){
            if((roleType==RoleType.COLLEGE_FINALIZATION_REVIEW)){
                return ProjectStatus.COLLEGE_FINAL_SUBMISSION;
            }
        }
        if(operationType == OperationType.FUNCTIONAL_PASSED_THE_EXAMINATION){
            if((roleType==RoleType.FUNCTIONAL_DEPARTMENT||roleType == RoleType.FUNCTIONAL_DEPARTMENT_LEADER)){
                return ProjectStatus.CONCLUDED;
            }
        }

        switch (roleType.getValue()){
            //?????????????????????
            case 3:
                keyProjectStatus = ProjectStatus.GUIDE_TEACHER_ALLOWED;
                    break;
            //??????????????????
            case 4:
                if (operationType == OperationType.AGREE){
                    keyProjectStatus = ProjectStatus.LAB_ALLOWED;
                }else {
                    keyProjectStatus = ProjectStatus.LAB_ALLOWED_AND_REPORTED;
                }
                break;
                //?????????????????????
            case 5:
                if (operationType == OperationType.AGREE){
                    keyProjectStatus = ProjectStatus.SECONDARY_UNIT_ALLOWED;
                }else {
                    keyProjectStatus = ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED;
                }
                break;
                //?????????????????????
            case 6:
                keyProjectStatus = ProjectStatus.ESTABLISH;
                    break;
            default:
                throw new GlobalException(CodeMsg.UNKNOWN_ROLE_TYPE_AND_OPERATION_TYPE);
        }
        return keyProjectStatus;
    }

    @Transactional(rollbackFor = GlobalException.class)
    public Result operateKeyProjectOfSpecifiedRoleAndOperation(RoleType roleType, OperationType operationType,
                                                        List<KeyProjectCheck> list){
        User user = getUserService.getCurrentUser();
        if (user == null){
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }

        //?????????????????????????????????????????????????????????
        Integer college = getUserService.getCurrentUser().getInstitute();
        if (college == null){
            throw new GlobalException(CodeMsg.COLLEGE_TYPE_NULL_ERROR);
        }

        //????????????
        List<OperationRecord> operationRecordList = new LinkedList<>();
        List<Long> idList = new LinkedList<>();
        for (KeyProjectCheck check:list) {
            ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(check.getProjectId());
//            UserProjectGroup userProjectGroup = userProjectGroupMapper.selectByProjectGroupIdAndUserId(check.getProjectId(), Long.valueOf(user.getCode()));

            //??????????????????????????????????????????????????????
//            if (userRoleService.validContainsUserRole(RoleType.MENTOR)) {
//                if (userProjectGroup != null && userProjectGroup.getMemberRole().equals(MemberRole.GUIDANCE_TEACHER.getValue())) {
////                    if(roleType != RoleType.COLLEGE_FINALIZATION_REVIEW ) {
//                        throw new GlobalException(CodeMsg.PERMISSION_DENNY);
////                    }
//                }
//            }


            if ( projectFileMapper.selectByProjectGroupIdAndMaterialType(check.getProjectId(),MaterialType.APPLY_MATERIAL.getValue(),null) == null) {
                throw new GlobalException(CodeMsg.KEY_PROJECT_APPLY_MATERIAL_EMPTY);
            }

            OperationRecord operationRecord = new OperationRecord();
            operationRecord.setOperationUnit(roleType.getValue());
            operationRecord.setOperationType(operationType.getValue());
            operationRecord.setOperationReason(check.getReason());
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));
            operationRecord.setRelatedId(check.getProjectId());
            operationRecord.setOperationCollege(user.getInstitute());

            //????????????
            operationRecordList.add(operationRecord);

            idList.add(check.getProjectId());
            if(operationType == OperationType.INTERIM_RETURN
            || operationType == OperationType.COLLEGE_RETURNS
            || operationType == OperationType.FUNCTIONAL_RETURNS
            || operationType == OperationType.FUNCTIONAL_ESTABLISH_RETURN){
                //????????????
                HitBackMessage hitBackMessage = new HitBackMessage();
                hitBackMessage.setContent("?????????:"+projectGroup.getProjectName()+"  ??????:"+check.getReason());
                UserProjectGroup leader = userProjectGroupMapper.getProjectLeader(check.getProjectId(), MemberRole.PROJECT_GROUP_LEADER.getValue());
                if(leader == null){
                    hitBackMessage.setReceiveUserId(userProjectGroupMapper.getProjectLeader(check.getProjectId(), MemberRole.GUIDANCE_TEACHER.getValue()).getUserId());
                }else {
                    hitBackMessage.setReceiveUserId(userProjectGroupMapper.getProjectLeader(check.getProjectId(), MemberRole.PROJECT_GROUP_LEADER.getValue()).getUserId());
                }
                hitBackMessage.setSender(user.getRealName());
                Date date = new Date();
                hitBackMessage.setSendTime(date);
                hitBackMessage.setIsRead(false);
                hitBackMessageMapper.insert(hitBackMessage);
            }
        }
        if (operationType == OperationType.REJECT ) {
            if (roleType == RoleType.LAB_ADMINISTRATOR || roleType == RoleType.MENTOR) {
                for (KeyProjectCheck check:list
                ) {
                    //?????????????????????????????????????????????
                    keyProjectStatusMapper.update(check.getProjectId(),ProjectStatus.REJECT_MODIFY.getValue());
                    //?????????????????????????????????????????????????????????
                    projectGroupMapper.updateProjectStatus(check.getProjectId(),ProjectStatus.LAB_ALLOWED.getValue());
                }
            }else{
                for (KeyProjectCheck check:list
                ) {
                    //???????????????????????????
                    keyProjectStatusMapper.update(check.getProjectId(),ProjectStatus.ESTABLISH_FAILED.getValue());

                    projectGroupMapper.updateProjectStatus(check.getProjectId(),ProjectStatus.ESTABLISH_FAILED.getValue());
                }
            }
        }else {
            //?????????????????????
            Integer nextProjectStatus = getNextStatusByRoleAndOperation(roleType, operationType).getValue();
            keyProjectStatusMapper.updateList(idList,nextProjectStatus);
        }
        operationRecordMapper.multiInsert(operationRecordList);
        return Result.success();
    }

    @Override
    public Result agreeKeyProjectByGuideTeacher(List<KeyProjectCheck> list) {
        //????????????????????????
        timeLimitService.validTime(TimeLimitType.TEACHER_KEY_CHECK_LIMIT);

        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.MENTOR, OperationType.AGREE,list);
    }

    @Override
    public Result agreeKeyProjectByLabAdministrator(List<KeyProjectCheck> list) {
        //????????????????????????????????????????????????

        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        if (college == null) {
            throw new GlobalException(CodeMsg.PARAM_CANT_BE_NULL);
        }
        //????????????????????????
        //????????????????????????
        ProjectReview projectReview = projectReviewMapper.selectByCollegeAndType(college,ProjectType.KEY.getValue());
        if(projectReview != null){
            //????????????
            return approveProjectReview(RoleType.LAB_ADMINISTRATOR,OperationType.AGREE,list);
        }
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.LAB_ADMINISTRATOR, OperationType.REPORT,list);
    }

    @Override
    public Result agreeKeyProjectBySecondaryUnit(List<KeyProjectCheck> list) {
        //??????????????????
        timeLimitService.validTime(TimeLimitType.SECONDARY_UNIT_CHECK_LIMIT);
        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        if (college == null) {
            throw new GlobalException(CodeMsg.PARAM_CANT_BE_NULL);
        }

//        //????????????????????????
//        //????????????????????????
//        ProjectReview projectReview = projectReviewMapper.selectByCollegeAndType(college,ProjectType.KEY.getValue());
//        if(projectReview != null){
//            //????????????
//            return approveProjectReview(RoleType.SECONDARY_UNIT,OperationType.AGREE,list);
//        }
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.SECONDARY_UNIT, OperationType.AGREE,list);
    }

    /**
     * ??????????????????????????????????????????
     * @param roleType
     * @param operationType
     * @param list
     * @return
     */
    @Transactional(rollbackFor = GlobalException.class)
    public Result approveProjectReview(RoleType roleType, OperationType operationType,
                                                               List<KeyProjectCheck> list){
        User user = getUserService.getCurrentUser();
        if (user == null){
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }

        //?????????????????????????????????????????????????????????
        Integer college = getUserService.getCurrentUser().getInstitute();
        if (college == null){
            throw new GlobalException(CodeMsg.COLLEGE_TYPE_NULL_ERROR);
        }

        //????????????
        List<OperationRecord> operationRecordList = new LinkedList<>();
        List<Long> idList = new LinkedList<>();
        for (KeyProjectCheck check:list) {

            if ( projectFileMapper.selectByProjectGroupIdAndMaterialType(check.getProjectId(),MaterialType.APPLY_MATERIAL.getValue(),null) == null) {
                throw new GlobalException(CodeMsg.KEY_PROJECT_APPLY_MATERIAL_EMPTY);
            }

            OperationRecord operationRecord = new OperationRecord();
            operationRecord.setOperationUnit(roleType.getValue());
            operationRecord.setOperationType(operationType.getValue());
            operationRecord.setOperationReason(check.getReason());
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));
            operationRecord.setRelatedId(check.getProjectId());
            operationRecord.setOperationCollege(user.getInstitute());

            //????????????
            operationRecordList.add(operationRecord);

            idList.add(check.getProjectId());
        }
        //?????????????????????
        Integer nextProjectStatus = ProjectStatus.PROJECT_REVIEW.getValue();
        keyProjectStatusMapper.updateList(idList,nextProjectStatus);

        operationRecordMapper.multiInsert(operationRecordList);
        return Result.success();
    }
    /**
     * ????????????????????????????????????
     * @param list
     * @return
     */
    @Override
    public Result agreeKeyProjectByFunctionalDepartment(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.AGREE,list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result collegeSetKeyScore(List<CollegeGiveScore> collegeGiveScores) {
        User user = getUserService.getCurrentUser();
        List<Long> ids = new LinkedList<Long>();

        List<ProjectReviewResult> projectReviewResults = new LinkedList<ProjectReviewResult>();
        for (CollegeGiveScore giveScore : collegeGiveScores) {
            if(giveScore.getScore()>100 || giveScore.getScore() < 0 ){
                throw new GlobalException(CodeMsg.SCORE_ERROR);
            }
            ProjectReviewResult reviewResult = new ProjectReviewResult();
            BeanUtils.copyProperties(giveScore,reviewResult);
            if(giveScore.getIsSupport()==0){
                reviewResult.setIsSupport(false);
            }else{
                reviewResult.setIsSupport(true);
            }
            reviewResult.setOperateUser(Long.valueOf(user.getCode()));
            ids.add(giveScore.getProjectId());
            projectReviewResults.add(reviewResult);
        }
        //????????????
        Result result = approveKeyProjectNormal(ids);
        if(result.getCode()!=0){
            throw new GlobalException(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
        }
        projectReviewResultMapper.multiInsert(projectReviewResults);
        return Result.success();
    }

    /**
     * ????????????????????????
     * @param list
     * @return
     */
    @Transactional(rollbackFor = GlobalException.class)
    public Result approveKeyProjectNormal(List<Long> list){
        User user = getUserService.getCurrentUser();
        if (user == null){
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }

        //?????????????????????????????????????????????????????????
        Integer college = getUserService.getCurrentUser().getInstitute();
        if (college == null){
            throw new GlobalException(CodeMsg.COLLEGE_TYPE_NULL_ERROR);
        }

        //????????????
        List<Long> idList = new LinkedList<>();
        for (Long id:list) {
//            if ( projectFileMapper.selectByProjectGroupIdAndMaterialType(id,MaterialType.APPLY_MATERIAL.getValue(),null) == null) {
//                throw new GlobalException(CodeMsg.KEY_PROJECT_APPLY_MATERIAL_EMPTY);
//            }
            idList.add(id);
        }
        //?????????????????????
        Integer nextProjectStatus = ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue();
        keyProjectStatusMapper.updateList(idList,nextProjectStatus);

        return Result.success();
    }

    /**
     * ??????????????????
     * @param list
     * @return
     */
    @Override
    public Result agreeIntermediateInspectionKeyProject(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.OFFLINE_CHECK,list);
    }

    @Override
    public Result agreeToBeConcludingKeyProject(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.CONCLUSION,list);
    }

    @Override
    public Result reportKeyProjectByLabAdministrator(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.LAB_ADMINISTRATOR, OperationType.REPORT,list);
    }

    /**
     * ????????????????????????
     * @param list
     * @return
     */
    @Override
    public Result rejectIntermediateInspectionKeyProject(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.OFFLINE_CHECK_REJECT,list);
    }

    @Override
    public Result rejectToBeConcludingKeyProject(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.CONCLUSION_REJECT,list);
    }

    /**
     * ??????????????????
     * @param list
     * @return
     */
    @Override
    public Result reportKeyProjectBySecondaryUnit(List<KeyProjectCheck> list) {
        //????????????
        //????????????????????????
        timeLimitService.validTime(TimeLimitType.SECONDARY_UNIT_REPORT_LIMIT);
        //????????????
        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        if (college == null){
            throw new GlobalException(CodeMsg.COLLEGE_TYPE_NULL_ERROR);
        }

//        CollegeLimit collegeLimit = collegeLimitMapper.selectByTypeAndCollege(college,ProjectType.KEY.getValue());
//        //???????????????
//        if(collegeLimit == null){
//            collegeLimit.setCrrentQuantity(0);
//            collegeLimit.setLimitCollege(college.byteValue());
//            collegeLimit.setProjectType(ProjectType.KEY.getValue().byteValue());
//            collegeLimitMapper.insert(collegeLimit);
//        }

        //??????????????????
//        for (KeyProjectCheck check : list) {
//            String serialNumber = projectGroupMapper.getMaxSerialNumberByCollege(college);
//            //??????????????????????????????????????????
//            projectGroupMapper.updateProjectSerialNumber(check.getProjectId(), SerialNumberUtil.getSerialNumberOfProject(college, ProjectType.KEY.getValue(), serialNumber));
//        }

        AmountAndTypeVO amountAndTypeVO = amountLimitMapper.getAmountAndTypeVOByCollegeAndProjectType(college,ProjectType.KEY.getValue(),RoleType.SECONDARY_UNIT.getValue());

        MaxBigFunds maxBigFunds = maxBigFundsMapper.selectByCollege(String.valueOf(user.getInstitute()));
        if(maxBigFunds == null){
            int sum = 0;
            for (KeyProjectCheck keyProjectCheck : list) {
                 Float funds = projectGroupMapper.selectByPrimaryKey(keyProjectCheck.getProjectId()).getApplyFunds();
                 if(funds == 5000){
                     sum++;
                 }
            }
            if(sum > amountAndTypeVO.getMaxAmount()*0.2){
                throw new GlobalException(CodeMsg.KEY_PROJECT_AMOUNT_LIMIT2);
            }
            MaxBigFunds maxBigFunds1 = new MaxBigFunds();
            maxBigFunds1.setCollege(String.valueOf(user.getInstitute()));
            maxBigFunds1.setNum(sum);

            maxBigFundsMapper.insert(maxBigFunds1);
        }else {
            maxBigFunds = maxBigFundsMapper.selectByCollege(String.valueOf(user.getInstitute()));
            int sum = maxBigFunds.getNum();

            for (KeyProjectCheck keyProjectCheck : list) {
                Float funds = projectGroupMapper.selectByPrimaryKey(keyProjectCheck.getProjectId()).getApplyFunds();
                if(funds == 5000){
                    sum++;
                }
            }
            if(sum > amountAndTypeVO.getMaxAmount()*0.2){
                throw new GlobalException(CodeMsg.KEY_PROJECT_AMOUNT_LIMIT2);
            }
            maxBigFunds.setNum(sum);
            maxBigFundsMapper.updateByPrimaryKey(maxBigFunds);

        }
        Integer currentAmount = keyProjectStatusMapper.getCountOfSpecifiedStatusAndProjectProject(ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED.getValue(),college);
        if (currentAmount + list.size() > amountAndTypeVO.getMaxAmount()) {
            throw new GlobalException(CodeMsg.KEY_PROJECT_AMOUNT_LIMIT);
        }

        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.SECONDARY_UNIT, OperationType.REPORT,list);
    }



    @Override
    public Result rejectKeyProjectByGuideTeacher(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.MENTOR, OperationType.REJECT,list);
    }

    @Override
    public Result getHistoricalKeyProjectInfo(HistoryQueryKeyProjectInfo info) {

        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        //?????????????????????????????????
        if (info.getOperationUnit().equals(OperationUnit.FUNCTIONAL_DEPARTMENT.getValue())) {
            college = null;
        }

        List<ProjectGroup> list;

        //???????????????????????????  ??????????????????????????????
        if (info.getOperationType().equals(OperationType.AGREE.getValue())
                || info.getOperationType().equals(OperationType.REPORT.getValue())) {
            //?????????????????????
            Integer status = 0;
            if (info.getOperationUnit().equals(OperationUnit.LAB_ADMINISTRATOR.getValue())) {
                if (info.getOperationType().equals(OperationType.AGREE.getValue())) {
                    status = ProjectStatus.LAB_ALLOWED.getValue();
                }else {
                    status = ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue();
                }
            }else if (info.getOperationUnit().equals(OperationUnit.SECONDARY_UNIT.getValue())) {
                if (info.getOperationType().equals(OperationType.AGREE.getValue())) {
                    status = ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue();
                }else {
                    status = ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED.getValue();
                }
            }else if (info.getOperationUnit().equals(OperationUnit.FUNCTIONAL_DEPARTMENT.getValue())) {
                college = null;
                //?????????????????????????????????????????????????????????????????????????????????-3????????????????????????
                status = ProjectStatus.ESTABLISH.getValue();
            }
            list =projectGroupMapper.selectKeyPassedProjectList(college,status);
        }else {
            list  = projectGroupMapper.selectKeyRejectedProjectList(college);
        }
        for (ProjectGroup projectGroup:list
        ) {
            projectGroup.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(projectGroup.getId(),null));
            projectGroup.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),projectGroup.getId(),JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }

    @Override
    public Result getToBeReportedProjectByLabAdmin() {
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.LAB_ALLOWED,null);
    }

    /**
     * ?????????????????????????????????
     * @return
     */
    @Override
    public Result getToBeReportedProjectBySecondaryUnit() {
        User user  = getUserService.getCurrentUser();
        // ????????????????????????
        //????????????????????????
        ProjectReview projectReview = projectReviewMapper.selectByCollegeAndType(user.getInstitute(),ProjectType.KEY.getValue());
        if(projectReview != null){
            //????????????
            return getReviewInfo();
        }
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.SECONDARY_UNIT_ALLOWED,user.getInstitute());
    }

    private Result getReviewInfo2() {

        User currentUser = getUserService.getCurrentUser();

        //??????????????????????????????
        List<ProjectReviewVO> projectReviewVOS = keyProjectStatusMapper.selectKeyHasReview(currentUser.getInstitute(),ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue());
        for (ProjectReviewVO projectReviewVO : projectReviewVOS) {
            //??????????????????????????????
            projectReviewVO.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),projectReviewVO.getId(),JoinStatus.JOINED.getValue()));
            projectReviewVO.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(projectReviewVO.getId(), JoinStatus.JOINED.getValue()));
        }


        return Result.success( SortListUtil.sort(projectReviewVOS,"score",SortListUtil.DESC));
    }
    private Result getReviewInfo() {

        User currentUser = getUserService.getCurrentUser();

        //??????????????????????????????
        List<ProjectReviewVO> projectReviewVOS = keyProjectStatusMapper.selectKeyHasReview(currentUser.getInstitute(),ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue());
        for (ProjectReviewVO projectReviewVO : projectReviewVOS) {
            //??????????????????????????????
            projectReviewVO.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),projectReviewVO.getId(),JoinStatus.JOINED.getValue()));
            projectReviewVO.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(projectReviewVO.getId(), JoinStatus.JOINED.getValue()));
        }


        return Result.success( SortListUtil.sort(projectReviewVOS,"score",SortListUtil.DESC));
    }
    @Override
    public Result getToReviewKeyProject() {
        User user  = getUserService.getCurrentUser();
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.PROJECT_REVIEW,user.getInstitute());
    }


    @Override
    public Result rejectKeyProjectByLabAdministrator(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.LAB_ADMINISTRATOR, OperationType.REJECT,list);
    }

    @Override
    public Result rejectKeyProjectBySecondaryUnit(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.SECONDARY_UNIT, OperationType.REJECT,list);
    }

    @Override
    public Result rejectKeyProjectReportBySecondaryUnit(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.SECONDARY_UNIT, OperationType.REPORT_REJECT,list);
    }

    /**
     * ????????????
     * @param list
     * @return
     */
    @Override
    public Result midTermKeyProjectHitBack(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.INTERIM_RETURN,list);
    }

    /**
     * ????????????????????????
     * @param list
     * @return
     */
    @Override
    public Result keyProjectEstablishHitBack(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.FUNCTIONAL_ESTABLISH_RETURN,list);
    }

    /**
     * ????????????
     * @param list
     * @return
     */
    @Override
    public Result collegeKeyProjectHitBack(List<KeyProjectCheck> list){
        if (!userRoleService.validContainsUserRole(RoleType.COLLEGE_FINALIZATION_REVIEW)) {
            throw new GlobalException(CodeMsg.PERMISSION_DENNY);
        }
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.COLLEGE_FINALIZATION_REVIEW, OperationType.COLLEGE_RETURNS,list);
    }

    /**
     * ??????????????????
     * @param list
     * @return
     */
    @Override
    public Result functionKeyProjectHitBack(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.FUNCTIONAL_RETURNS,list);
    }

    @Override
    public Result rejectCollegeKeyProject(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.COLLEGE_FINALIZATION_REVIEW, OperationType.CONCLUSION_REJECT,list);
    }

    @Override
    public Result collegeGivesKeyProjectRating(List<KeyProjectCheck> list) {
//        User user = getUserService.getCurrentUser();
//
//        for (ProjectGrade projectGrade : list) {
//            if (!keyProjectStatusMapper.getStatusByProjectId(projectGrade.getProjectId()).equals(ProjectStatus.ESTABLISH.getValue())) {
//                throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
//            }
//            KeyProjectCheck projectCheckForm = new KeyProjectCheck();
//            BeanUtils.copyProperties(projectGrade,projectCheckForm);
//            projectCheckForm.setReason("????????????????????????????????????"+KeyGrade.getTips(projectGrade.getValue()));
//            list.add(projectCheckForm);
//        }
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.COLLEGE_FINALIZATION_REVIEW, OperationType.COLLEGE_PASSED_THE_EXAMINATION,list);
    }

    @Override
    public Result functionGivesKeyProjectRating(List<KeyProjectCheck> list) {
//        User user = getUserService.getCurrentUser();
//        List<KeyProjectCheck> list = new LinkedList<>();
//        for (ProjectGrade projectGrade : projectGradeList) {
//
//            if (!keyProjectStatusMapper.getStatusByProjectId(projectGrade.getProjectId()).equals(ProjectStatus.COLLEGE_FINAL_SUBMISSION.getValue())) {
//                if(!keyProjectStatusMapper.getCollegeByProjectId(projectGrade.getProjectId()).equals(CollegeType.FUNCTIONAL_DEPARTMENT.getValue())){
//                    throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
//                }
//            }
//            KeyProjectCheck projectCheckForm = new KeyProjectCheck();
//            BeanUtils.copyProperties(projectGrade,projectCheckForm);
//            projectCheckForm.setReason("???????????????????????????,?????????"+KeyGrade.getTips(projectGrade.getValue()));
//            list.add(projectCheckForm);
//        }
//        functionSetProjectGrade(projectGradeList,user,2);
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.FUNCTIONAL_PASSED_THE_EXAMINATION,list);
    }

    private void functionSetProjectGrade(List<ProjectGrade> projectGradeList,User user,Integer projectType){
        for (ProjectGrade projectGrade : projectGradeList) {
            FunctionGivesGrade functionGivesGrade = new FunctionGivesGrade();
            functionGivesGrade.setOperatorName(user.getRealName());
            functionGivesGrade.setAcceptanceTime(new Date());
            functionGivesGrade.setGrade(projectGrade.getValue());
            functionGivesGrade.setProjectId(projectGrade.getProjectId());
            functionGivesGrade.setProjectType(projectType);
            functionGivesGradeMapper.insert(functionGivesGrade);
        }
        log.info("????????????");
    }
    private void setProjectGrade(List<ProjectGrade> projectGradeList,User user,Integer projectType){
        for (ProjectGrade projectGrade : projectGradeList) {
            CollegeGivesGrade collegeGivesGrade = new CollegeGivesGrade();
            collegeGivesGrade.setOperatorName(user.getRealName());
            collegeGivesGrade.setAcceptanceTime(new Date());
            collegeGivesGrade.setGrade(projectGrade.getValue());
            collegeGivesGrade.setProjectId(projectGrade.getProjectId());
            collegeGivesGrade.setProjectType(projectType);
            collegeGivesGradeMapper.insert(collegeGivesGrade);
        }
        log.info("????????????");
    }

    @Override
    public Result getMidTermReturnProject() {
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.INTERIM_RETURN_MODIFICATION,null);
    }

    @Override
    public Result getKeyProjectEstablishReturnProject() {
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.FUNCTIONAL_ESTABLISH_RETURNS,null);
    }

    /**
     * ??????????????????????????????
     * @return
     */
    @Override
    public Result getCollegeReturnKeyProject() {
        User user  = getUserService.getCurrentUser();
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.COLLEGE_RETURNS,user.getInstitute());
    }

    @Override
    public Result getFunctionReturnKeyProject(Integer college) {
        return getKeyProjectDTOListByStatusAndCollege(ProjectStatus.FUNCTIONAL_RETURNS,college);
    }


    /**
     * ????????????????????????
     * @param list
     * @return
     */
    @Override
    public Result midTermReviewPassed(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.MIDTERM_REVIEW_PASSED,list);
    }

    /**
     * ??????????????????
     * @param list
     * @return
     */
    @Override
    public Result keyProjectEstablishReviewPassed(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.FUNCTIONAL_ESTABLISH_PASSED,list);
    }

    @Override
    public Result collegeReviewPassed(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.COLLEGE_FINALIZATION_REVIEW, OperationType.COLLEGE_REVIEW_PASSED,list);
    }

    @Override
    public Result functionReviewPassed(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.FUNCTIONAL_REVIEW_PASSED,list);
    }

    @Override
    public Result rejectKeyProjectByFunctionalDepartment(List<KeyProjectCheck> list) {
        return operateKeyProjectOfSpecifiedRoleAndOperation(RoleType.FUNCTIONAL_DEPARTMENT, OperationType.REJECT,list);
    }

    @Override
    public Result getKeyProjectDetailById(Long projectId) {
        List<ProjectHistoryInfo> list = operationRecordMapper.selectAllOfKeyProjectByProjectId(projectId);
        return Result.success(list);
    }

    @Override
    public Result conditionallyQueryOfKeyProject(QueryConditionForm form){
        return conditionallyQueryOfCheckedProject(form);
    }

    private Result conditionallyQueryOfCheckedProject(QueryConditionForm form) {
        List<Long> projectIdList = projectGroupMapper.conditionKeyQueryOfKeyProject(form);


        if (projectIdList.isEmpty()){
            return Result.success(null);
        }
        List<ProjectGroup> list = projectGroupMapper.selectAllByList(projectIdList);
        for (ProjectGroup projectGroup:list
        ) {
            Long id = projectGroup.getId();
            projectGroup.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(id,null));
            projectGroup.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),id, JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }

}
