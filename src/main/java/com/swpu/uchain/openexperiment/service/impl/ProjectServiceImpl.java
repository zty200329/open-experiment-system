package com.swpu.uchain.openexperiment.service.impl;

import com.swpu.uchain.openexperiment.DTO.OperationRecord;
import com.swpu.uchain.openexperiment.DTO.ProjectHistoryInfo;
import com.swpu.uchain.openexperiment.VO.limit.AmountAndTypeVO;
import com.swpu.uchain.openexperiment.VO.project.*;
import com.swpu.uchain.openexperiment.VO.user.UserMemberVO;
import com.swpu.uchain.openexperiment.VO.user.UserVO;
import com.swpu.uchain.openexperiment.config.UploadConfig;
import com.swpu.uchain.openexperiment.domain.*;
import com.swpu.uchain.openexperiment.enums.*;
import com.swpu.uchain.openexperiment.exception.GlobalException;
import com.swpu.uchain.openexperiment.form.member.MemberQueryCondition;
import com.swpu.uchain.openexperiment.form.project.*;
import com.swpu.uchain.openexperiment.form.query.HistoryQueryProjectInfo;
import com.swpu.uchain.openexperiment.form.query.QueryConditionForm;
import com.swpu.uchain.openexperiment.mapper.*;
import com.swpu.uchain.openexperiment.redis.RedisService;
import com.swpu.uchain.openexperiment.redis.key.ProjectGroupKey;
import com.swpu.uchain.openexperiment.result.Result;
import com.swpu.uchain.openexperiment.service.*;
import com.swpu.uchain.openexperiment.util.ConvertUtil;
import com.swpu.uchain.openexperiment.util.RedisUtil;
import com.swpu.uchain.openexperiment.util.SerialNumberUtil;
import com.swpu.uchain.openexperiment.util.SortListUtil;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;


/**
 * @author panghu
 */
@Service
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private UserService userService;
    private ProjectGroupMapper projectGroupMapper;
    private RedisService redisService;
    private UserProjectService userProjectService;
    private ProjectFileService projectFileService;
    private FundsService fundsService;
    private UploadConfig uploadConfig;
    private ConvertUtil convertUtil;
    private GetUserService getUserService;
    private UserProjectGroupMapper userProjectGroupMapper;
    private RoleMapper roleMapper;
    private UserRoleService userRoleService;
    private OperationRecordMapper recordMapper;
    private UserMapper userMapper;
    private KeyProjectStatusMapper keyProjectStatusMapper;
    private ProjectFileMapper projectFileMapper;
    private TimeLimitService timeLimitService;
    private AmountLimitMapper amountLimitMapper;
    private HitBackMessageMapper hitBackMessageMapper;
    private RedisUtil redisUtil;
    private AchievementMapper achievementMapper;
    private CollegeGivesGradeMapper collegeGivesGradeMapper;
    private FunctionGivesGradeMapper functionGivesGradeMapper;
    private ProjectReviewMapper projectReviewMapper;
    private ProjectReviewResultMapper projectReviewResultMapper;
    private UserProjectAccountMapper userProjectAccountMapper;

    @Autowired
    public ProjectServiceImpl(UserService userService, ProjectGroupMapper projectGroupMapper,
                              RedisService redisService, UserProjectService userProjectService,
                              ProjectFileService projectFileService, FundsService fundsService,
                              UploadConfig uploadConfig,
                              ConvertUtil convertUtil, GetUserService getUserService,
                              OperationRecordMapper recordMapper,
                              RoleMapper roleMapper, AmountLimitMapper amountLimitMapper,
                              UserProjectGroupMapper userProjectGroupMapper, UserMapper userMapper,
                              KeyProjectStatusMapper keyProjectStatusMapper, ProjectFileMapper projectFileMapper,
                              TimeLimitService timeLimitService, RedisUtil redisUtil, UserRoleService userRoleService,
                              HitBackMessageMapper hitBackMessageMapper, AchievementMapper achievementMapper,
                              CollegeGivesGradeMapper collegeGivesGradeMapper, FunctionGivesGradeMapper functionGivesGradeMapper,
                              ProjectReviewMapper projectReviewMapper,ProjectReviewResultMapper projectReviewResultMapper,
                              UserProjectAccountMapper userProjectAccountMapper) {
        this.userService = userService;
        this.projectGroupMapper = projectGroupMapper;
        this.redisService = redisService;
        this.userProjectService = userProjectService;
        this.projectFileService = projectFileService;
        this.fundsService = fundsService;
        this.uploadConfig = uploadConfig;
        this.convertUtil = convertUtil;
        this.getUserService = getUserService;
        this.userProjectGroupMapper = userProjectGroupMapper;
        this.recordMapper = recordMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.keyProjectStatusMapper = keyProjectStatusMapper;
        this.projectFileMapper = projectFileMapper;
        this.timeLimitService = timeLimitService;
        this.amountLimitMapper = amountLimitMapper;
        this.userRoleService = userRoleService;
        this.hitBackMessageMapper = hitBackMessageMapper;
        this.redisUtil = redisUtil;
        this.achievementMapper = achievementMapper;
        this.collegeGivesGradeMapper = collegeGivesGradeMapper;
        this.functionGivesGradeMapper = functionGivesGradeMapper;
        this.projectReviewMapper = projectReviewMapper;
        this.projectReviewResultMapper = projectReviewResultMapper;
        this.userProjectAccountMapper = userProjectAccountMapper;
    }

    @Override
    public boolean insert(ProjectGroup projectGroup) {
        return projectGroupMapper.insert(projectGroup) == 1;
    }

    @Override
    public boolean update(ProjectGroup projectGroup) {
        projectGroup.setUpdateTime(new Date());
        return projectGroupMapper.updateByPrimaryKey(projectGroup) == 1;
    }

    @Override
    public void delete(Long projectGroupId) {
        redisService.delete(ProjectGroupKey.getByProjectGroupId, projectGroupId + "");
        projectGroupMapper.deleteByPrimaryKey(projectGroupId);
        //?????????????????????????????????
        userProjectService.deleteByProjectGroupId(projectGroupId);
        //TODO,???????????????????????????,??????,????????????,??????

    }

    @Override
    public ProjectGroup selectByProjectGroupId(Long projectGroupId) {
        ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectGroupId);
        //??????????????????????????????????????????????????????????????????
        if (projectGroup.getWhetherCommitKeyApply() != null) {
            projectGroup.setStatus(projectGroup.getWhetherCommitKeyApply());
        }
        return projectGroupMapper.selectByPrimaryKey(projectGroupId);
    }

    /**
     * ???????????????
     *
     * @param projectGroup
     * @return
     */
    private Result addProjectGroup(ProjectGroup projectGroup) {
        projectGroup.setCreateTime(new Date());
        projectGroup.setUpdateTime(new Date());
        if (insert(projectGroup)) {
            return Result.success();
        }
        return Result.error(CodeMsg.ADD_ERROR);
    }

    @Override
    public List<ProjectGroup> selectByUserIdAndProjectStatus(Long userId, Integer projectStatus, Integer joinStatus) {
        //???????????????????????????????????????
        return projectGroupMapper.selectByUserIdAndStatus(userId, projectStatus, joinStatus);
    }

    private List<CheckProjectVO> selectFunctionCreateCommonApply() {
        return projectGroupMapper.selectFunctionCreateCommonApply();
    }

    /**
     * ?????????????????????????????????
     * <p>
     * ??????
     *
     * @param form ??????????????????
     * @return ????????????????????????
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public synchronized Result applyCreateProject(CreateProjectApplyForm form) {
        //??????????????????
        timeLimitService.validTime(TimeLimitType.DECLARE_LIMIT);

        //??????????????????
        if (form.getFitPeopleNum() > 6 || form.getFitPeopleNum() < 2) {
            throw new GlobalException(CodeMsg.FIT_PEOPLE_ERROR);
        }

        User currentUser = getUserService.getCurrentUser();

        //?????????????????? ???????????????????????????
        if (!userRoleService.validContainsUserRole(RoleType.MENTOR)) {
            throw new GlobalException(CodeMsg.ONLY_TEACHER_CAN_APPLY);
        }
        //??????????????????
        Integer college = currentUser.getInstitute();
        if (college == null) {
            throw new GlobalException(CodeMsg.COLLEGE_TYPE_NULL_ERROR);
        }

        //??????????????????????????????????????????
        UserProjectAccount userProjectAccount = userProjectAccountMapper.selectByCode(currentUser.getCode());
        //?????????????????????
        if(userProjectAccount != null) {
            if ((userProjectAccount.getKeyNum()+userProjectAccount.getGeneralNum()) >= 4){
                throw new GlobalException(CodeMsg.MAX_NUM_OF_TYPE);
            }else if(form.getProjectType().equals(ProjectType.GENERAL.getValue()) && userProjectAccount.getGeneralNum() >= 3){
                throw new GlobalException(CodeMsg.MAX_NUM_OF_TYPE);
            }else if(form.getProjectType().equals(ProjectType.KEY.getValue()) && userProjectAccount.getKeyNum() >= 1){
                throw new GlobalException(CodeMsg.MAX_NUM_OF_TYPE);
            }else{
                if(form.getProjectType().equals(ProjectType.GENERAL.getValue())){
                    userProjectAccount.setGeneralNum(userProjectAccount.getGeneralNum()+1);
                }else {
                    userProjectAccount.setKeyNum(userProjectAccount.getKeyNum()+1);
                }
                userProjectAccountMapper.updateByPrimaryKey(userProjectAccount);
            }
            //?????????
        }else{
            UserProjectAccount userAccount = new UserProjectAccount();
            userAccount.setCode(currentUser.getCode());
            userAccount.setCollege(String.valueOf(currentUser.getInstitute()));
            userAccount.setUserType(2);
            if(form.getProjectType().equals(ProjectType.GENERAL.getValue())){
                userAccount.setGeneralNum(1);
                userAccount.setKeyNum(0);
            }else {
                userAccount.setGeneralNum(0);
                userAccount.setKeyNum(1);
            }
            userProjectAccountMapper.insert(userAccount);
        }
//        AmountAndTypeVO amountAndTypeVO = amountLimitMapper.getAmountAndTypeVOByCollegeAndProjectType(null, form.getProjectType(), RoleType.MENTOR.getValue());
//        if (amountAndTypeVO != null) {
//            Integer maxAmount = amountAndTypeVO.getMaxAmount();
//            Integer currentCount = userProjectGroupMapper.geCountOfAppliedProject(Long.valueOf(currentUser.getCode()), form.getProjectType());
//            if (maxAmount >= currentCount) {
//                return Result.error(CodeMsg.MAXIMUM_APPLICATION);
//            }
//        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        //????????????
        try {
            Date startTime = dateFormat.parse("2020-12-05");
            form.setStartTime(startTime);
            Date endTime;
            if (form.getProjectType().equals(ProjectType.GENERAL.getValue())) {
                endTime = dateFormat.parse("2021-06-01");
            } else {
                endTime = dateFormat.parse("2021-11-01");
            }
            form.setEndTime(endTime);
        } catch (ParseException e) {
            throw new GlobalException(CodeMsg.TIME_DEFINE_ERROR);
        }



        //???????????????,?????????????????????
        if (form.getIsOpenTopic().equals(OpenTopicType.OPEN_TOPIC_ALL.getValue()) && form.getStuCodes() != null) {
            throw new GlobalException(CodeMsg.TOPIC_IS_NOT_OPEN);
        }

        //??????????????????
        if (form.getStartTime().getTime() >= form.getEndTime().getTime()) {
            throw new GlobalException(CodeMsg.TIME_DEFINE_ERROR);
        }

        ProjectGroup projectGroup = new ProjectGroup();
        BeanUtils.copyProperties(form, projectGroup);
        projectGroup.setStatus(ProjectStatus.DECLARE.getValue());
        //???????????????
        projectGroup.setCreatorId(Long.valueOf(currentUser.getCode()));
        projectGroup.setSubordinateCollege(college);
        //????????????
        Result result = addProjectGroup(projectGroup);
        if (result.getCode() != 0) {
            throw new GlobalException(CodeMsg.ADD_PROJECT_GROUP_ERROR);
        }

        //????????????????????????
        String maxTempSerialNumber = null;
        if(form.getProjectType() == 1){
            maxTempSerialNumber = projectGroupMapper.getMaxTempSerialNumberByCollege(college,1);
        }else{
            maxTempSerialNumber = projectGroupMapper.getMaxTempSerialNumberByCollege(college,2);
        }
        //??????????????????????????????????????????
        projectGroupMapper.updateProjectTempSerialNumber(projectGroup.getId(), SerialNumberUtil.getSerialNumberOfProject(college, form.getProjectType(), maxTempSerialNumber));


        String[] teacherArray = new String[1];
        teacherArray[0] = currentUser.getCode();
        String secondTeacherCode = form.getAnotherTeacherCodes();
        if (secondTeacherCode != null) {
            teacherArray = new String[2];
            teacherArray[0] = currentUser.getCode();
            teacherArray[1] = secondTeacherCode;
            //????????????????????????
            User secondTeacher = userMapper.selectByUserCode(secondTeacherCode);
            if (userMapper.selectByUserCode(secondTeacherCode) != null) {
                //????????????
                if (!userRoleService.validContainsUserRole(secondTeacher, RoleType.MENTOR) &&
                        !userRoleService.validContainsUserRole(secondTeacher, RoleType.POSTGRADUATE)) {
                    throw new GlobalException(CodeMsg.THIS_USER_CANNOT_BE_A_TUTOR);
                }
            } else {
                throw new GlobalException(CodeMsg.ADD_TEACHER_NOT_EXIST);
            }
        }



        String[] stuCodes = form.getStuCodes();
        //????????????
        if (stuCodes != null && stuCodes.length > form.getFitPeopleNum()) {
//            for (String stuCode : stuCodes) {
//                //??????????????????????????????(??????????????????????????????)
//                User addUser = userMapper.selectByUserCode(stuCode);
//                if (addUser.getMobilePhone() == null) {
//                    throw new GlobalException(CodeMsg.ADD_USER_INFO_NOT_COMPLETE);
//                }
//            }
            throw new GlobalException(CodeMsg.FIT_PEOPLE_LIMIT_ERROR);
        }

        if(stuCodes != null) {
            for (String stuCode : stuCodes) {
                UserProjectAccount userProjectAccount1 = userProjectAccountMapper.selectByCode(stuCode);
                //?????????????????????
                if (userProjectAccount1 != null) {
                    if (userProjectAccount1.getKeyNum() + userProjectAccount1.getGeneralNum() >= 3) {
                        throw new GlobalException(CodeMsg.STU_MAX_NUM_OF_TYPE);
                    } else {
                        if (form.getProjectType().equals(ProjectType.GENERAL.getValue())) {
                            userProjectAccount1.setGeneralNum((userProjectAccount1.getGeneralNum() + 1));
                        } else {
                            userProjectAccount1.setKeyNum((userProjectAccount1.getKeyNum() + 1));
                        }
                        userProjectAccountMapper.updateByPrimaryKey(userProjectAccount1);
                    }
                    //?????????
                } else {
                    UserProjectAccount userAccount = new UserProjectAccount();
                    userAccount.setCode(stuCode);
                    userAccount.setCollege(String.valueOf(userMapper.selectByUserCode(stuCode).getInstitute()));
                    userAccount.setUserType(1);
                    if(form.getProjectType().equals(ProjectType.GENERAL.getValue())){
                        userAccount.setGeneralNum(1);
                        userAccount.setKeyNum(0);
                    }else {
                        userAccount.setGeneralNum(0);
                        userAccount.setKeyNum(1);
                    }

                    userProjectAccountMapper.insert(userAccount);
                }
            }
        }
        userProjectService.addStuAndTeacherJoin(stuCodes, teacherArray, projectGroup.getId());
        //??????????????????
        OperationRecord operationRecord = new OperationRecord();
        operationRecord.setRelatedId(projectGroup.getId());
        operationRecord.setOperationType(OperationType.REPORT.getValue());
        operationRecord.setOperationUnit(OperationUnit.MENTOR.getValue());
        operationRecord.setOperationExecutorId(Long.valueOf(currentUser.getCode()));
        return Result.success();
    }

    /**
     * ?????????????????? ?????????????????????
     *
     * @param form
     * @return
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Result FunctionCreateCommonApply(FunctionCreateProjectApplyForm form) {
        //??????????????????

        //??????????????????
        if (form.getFitPeopleNum() > 6 || form.getFitPeopleNum() < 2) {
            throw new GlobalException(CodeMsg.FIT_PEOPLE_ERROR);
        }

        User currentUser = getUserService.getCurrentUser();

        //???????????????

        //???????????????

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        //????????????
        try {
            Date startTime = dateFormat.parse("2020-12-05");
            form.setStartTime(startTime);
            Date endTime;
            if (form.getProjectType().equals(ProjectType.GENERAL.getValue())) {
                endTime = dateFormat.parse("2021-06-01");
            } else {
                endTime = dateFormat.parse("2021-11-01");
            }
            form.setEndTime(endTime);
        } catch (ParseException e) {
            throw new GlobalException(CodeMsg.TIME_DEFINE_ERROR);
        }

        //?????????????????? ????????????????????????????????????????????????
        if (!userRoleService.validContainsUserRole(RoleType.FUNCTIONAL_DEPARTMENT) &&
                !userRoleService.validContainsUserRole(RoleType.FUNCTIONAL_DEPARTMENT_LEADER)) {
            throw new GlobalException(CodeMsg.DOES_NOT_HAVE_DEFAULT_PROJECT_PERMISSIONS);
        }

        //??????????????????
        if (form.getStartTime().getTime() >= form.getEndTime().getTime()) {
            throw new GlobalException(CodeMsg.TIME_DEFINE_ERROR);
        }
        ProjectGroup projectGroup = new ProjectGroup();
        BeanUtils.copyProperties(form, projectGroup);
        projectGroup.setStatus(ProjectStatus.ESTABLISH.getValue());
        //????????????
        projectGroup.setIsOpenTopic(2);

        //???????????????
        projectGroup.setCreatorId(Long.valueOf(form.getTeacherCodes()));
        projectGroup.setSubordinateCollege(CollegeType.FUNCTIONAL_DEPARTMENT.getValue());
        //????????????
        Result result = addProjectGroup(projectGroup);
        if (result.getCode() != 0) {
            throw new GlobalException(CodeMsg.ADD_PROJECT_GROUP_ERROR);
        }
        if (form.getProjectType() == 2) {
            keyProjectStatusMapper.insert(projectGroup.getId(), ProjectStatus.ESTABLISH.getValue(),
                    projectGroup.getSubordinateCollege(), Long.valueOf(currentUser.getCode()));
        }
        String itemNumber = form.getItemNumber();
        projectGroupMapper.updateProjectTempSerialNumber(projectGroup.getId(), itemNumber);

        String[] teacherArray = new String[1];
        teacherArray[0] = form.getTeacherCodes();
        String secondTeacherCode = form.getAnotherTeacherCodes();
        if (secondTeacherCode != null) {
            teacherArray = new String[2];
            teacherArray[0] = form.getTeacherCodes();
            teacherArray[1] = secondTeacherCode;
            //????????????????????????
            if (userMapper.selectByUserCode(secondTeacherCode) == null) {
                throw new GlobalException(CodeMsg.ADD_TEACHER_NOT_EXIST);
            }
        }

        String[] stuCodes = form.getStuCodes();
        //????????????
        if (stuCodes != null && stuCodes.length > form.getFitPeopleNum()) {
            throw new GlobalException(CodeMsg.FIT_PEOPLE_LIMIT_ERROR);
        }
        userProjectService.addStuAndTeacherJoin(stuCodes, teacherArray, projectGroup.getId());

        //??????????????????
        OperationRecord operationRecord = new OperationRecord();
        operationRecord.setRelatedId(projectGroup.getId());
        operationRecord.setOperationType(OperationType.AGREE.getValue());
        operationRecord.setOperationUnit(OperationUnit.FUNCTIONAL_DEPARTMENT.getValue());
        operationRecord.setOperationExecutorId(Long.valueOf(currentUser.getCode()));
        recordMapper.insert(operationRecord);
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Result applyUpdateProject(UpdateProjectApplyForm updateProjectApplyForm) {

        //TODO ?????????????????????
        //??????????????????
//        timeLimitService.validTime(TimeLimitType.DECLARE_LIMIT);

        ProjectGroup projectGroup = selectByProjectGroupId(updateProjectApplyForm.getProjectGroupId());
        if (projectGroup == null) {
            return Result.error(CodeMsg.PROJECT_GROUP_NOT_EXIST);
        }
        User currentUser = getUserService.getCurrentUser();
        UserProjectGroup userProjectGroup = userProjectService.selectByProjectGroupIdAndUserId(updateProjectApplyForm.getProjectGroupId(), Long.valueOf(currentUser.getCode()));
        if (userProjectGroup == null) {
            return Result.error(CodeMsg.USER_NOT_IN_GROUP);
        }
        if(projectGroup.getProjectType() == 1) {
            //?????????????????????????????????????????????????????????????????????
            if (!(projectGroup.getStatus().equals(ProjectStatus.REJECT_MODIFY.getValue()) || projectGroup.getStatus().equals(ProjectStatus.INTERIM_RETURN_MODIFICATION.getValue()) ||
                    projectGroup.getStatus().equals(ProjectStatus.DECLARE.getValue()) || projectGroup.getStatus().equals(ProjectStatus.FUNCTIONAL_ESTABLISH_RETURNS.getValue())
                    || projectGroup.getStatus().equals(ProjectStatus.LAB_ALLOWED.getValue()))) {
                return Result.error(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
        }else{
            //???????????????????????????
            if(projectGroup.getKeyProjectStatus() != null) {
                //?????????????????????????????????????????????????????????????????????
                if (!(projectGroup.getKeyProjectStatus().equals(ProjectStatus.REJECT_MODIFY.getValue()) || projectGroup.getKeyProjectStatus().equals(ProjectStatus.INTERIM_RETURN_MODIFICATION.getValue()) ||
                        projectGroup.getKeyProjectStatus().equals(ProjectStatus.DECLARE.getValue()) || projectGroup.getKeyProjectStatus().equals(ProjectStatus.FUNCTIONAL_ESTABLISH_RETURNS.getValue())
                        || projectGroup.getKeyProjectStatus().equals(ProjectStatus.LAB_ALLOWED.getValue()) || projectGroup.getKeyProjectStatus().equals(ProjectStatus.KEY_PROJECT_APPLY.getValue())
                        || projectGroup.getKeyProjectStatus().equals(ProjectStatus.TO_DE_CONFIRMED.getValue()) || projectGroup.getKeyProjectStatus().equals(ProjectStatus.GUIDE_TEACHER_ALLOWED.getValue()))) {
                    return Result.error(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
                }
            }else {
                if (!(projectGroup.getStatus().equals(ProjectStatus.REJECT_MODIFY.getValue()) || projectGroup.getStatus().equals(ProjectStatus.INTERIM_RETURN_MODIFICATION.getValue()) ||
                        projectGroup.getStatus().equals(ProjectStatus.DECLARE.getValue()) || projectGroup.getStatus().equals(ProjectStatus.FUNCTIONAL_ESTABLISH_RETURNS.getValue())
                        || projectGroup.getStatus().equals(ProjectStatus.LAB_ALLOWED.getValue()))) {
                    return Result.error(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
                }
            }
        }
        //???????????????????????????????????????????????????????????????
        if (!projectGroup.getStatus().equals(ProjectStatus.INTERIM_RETURN_MODIFICATION.getValue())&& !projectGroup.getStatus().equals(ProjectStatus.FUNCTIONAL_ESTABLISH_RETURNS.getValue())) {
            projectGroup.setStatus(ProjectStatus.DECLARE.getValue());
        }

        //???????????????
        projectGroup.setCreatorId(Long.valueOf(currentUser.getCode()));
        //????????????
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date startTime = dateFormat.parse("2020-12-05");
            updateProjectApplyForm.setStartTime(startTime);
            Date endTime;
            if (updateProjectApplyForm.getProjectType().equals(ProjectType.GENERAL.getValue())) {
                endTime = dateFormat.parse("2021-06-01");
            } else {
                endTime = dateFormat.parse("2021-11-01");
            }
            updateProjectApplyForm.setEndTime(endTime);
        } catch (ParseException e) {
            throw new GlobalException(CodeMsg.TIME_DEFINE_ERROR);
        }



        if(!projectGroup.getProjectType().equals(updateProjectApplyForm.getProjectType())){
            //????????????????????????
            String maxTempSerialNumber = null;
            if(updateProjectApplyForm.getProjectType() == 1){
                maxTempSerialNumber = projectGroupMapper.getMaxTempSerialNumberByCollege(projectGroup.getSubordinateCollege(),1);
            }else{
                maxTempSerialNumber = projectGroupMapper.getMaxTempSerialNumberByCollege(projectGroup.getSubordinateCollege(),2);
            }
            //??????????????????????????????????????????
            projectGroupMapper.updateProjectTempSerialNumber(projectGroup.getId(), SerialNumberUtil.getSerialNumberOfProject(projectGroup.getSubordinateCollege(), updateProjectApplyForm.getProjectType(), maxTempSerialNumber));
        }

        //???????????????????????????
        BeanUtils.copyProperties(updateProjectApplyForm, projectGroup);
        update(projectGroup);

        OperationRecord operationRecord = new OperationRecord();
        operationRecord.setOperationCollege(currentUser.getInstitute());
        operationRecord.setRelatedId(updateProjectApplyForm.getProjectGroupId());
        operationRecord.setOperationType(OperationType.MODIFY.getValue());
        operationRecord.setOperationUnit(OperationUnit.MENTOR.getValue());
        operationRecord.setOperationExecutorId(Long.valueOf(currentUser.getCode()));
        //???????????????
        setOperationExecutor(operationRecord);


        //??????????????????
        if (!projectGroup.getStatus().equals(ProjectStatus.INTERIM_RETURN_MODIFICATION.getValue())) {
            projectGroupMapper.updateProjectStatus(projectGroup.getId(), ProjectStatus.DECLARE.getValue());
        }
        recordMapper.insert(operationRecord);
        return Result.success();
    }

    /**
     * ????????????
     * @param list
     * @return
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Result instructorsToDeleteItems(List<ProjectCheckForm> list) {
        User user = getUserService.getCurrentUser();
        if (user == null){
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        //??????????????????????????????????????????????????????
        for (ProjectCheckForm projectCheckForm : list) {
            ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectCheckForm.getProjectId());
            UserProjectGroup userProjectGroup = userProjectGroupMapper.selectByProjectGroupIdAndUserId(projectCheckForm.getProjectId(), Long.valueOf(user.getCode()));
            if (userRoleService.validContainsUserRole(RoleType.MENTOR)) {
                if (!(userProjectGroup != null && userProjectGroup.getMemberRole().equals(MemberRole.GUIDANCE_TEACHER.getValue()))){
                        throw new GlobalException(CodeMsg.PERMISSION_DENNY);
                }
            }
            //????????????
            if (!(projectGroup.getStatus() >= -2 && projectGroup.getStatus() <= 2) && !(projectGroup.getStatus() == -4)) {
                return Result.error(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
            if(projectGroup.getProjectType() == 2){
                keyProjectStatusMapper.deleteByProjectId(projectGroup.getId());
            }
            List<UserProjectGroup> userProjectGroups = userProjectGroupMapper.selectByProjectGroupId(projectCheckForm.getProjectId());
            log.info(userProjectGroups.toString());
            for (UserProjectGroup group : userProjectGroups) {
                //??????????????????
                UserProjectAccount userProjectAccount2 = userProjectAccountMapper.selectByCode(String.valueOf(group.getUserId()));
                //?????????????????????
                if(userProjectAccount2 != null) {
                    if (projectGroup.getProjectType().equals(ProjectType.GENERAL.getValue())) {
                        userProjectAccount2.setGeneralNum(userProjectAccount2.getGeneralNum() - 1);
                    } else {
                        userProjectAccount2.setKeyNum(userProjectAccount2.getKeyNum() - 1);
                    }
                    userProjectAccountMapper.updateByPrimaryKey(userProjectAccount2);
                }
            }

            projectReviewResultMapper.deleteByProjectId(projectCheckForm.getProjectId());
            projectGroupMapper.deleteByPrimaryKey(projectCheckForm.getProjectId());
            userProjectGroupMapper.deleteByProjectGroupId(projectCheckForm.getProjectId());
            recordMapper.deleteByGroupId(projectCheckForm.getProjectId());
        }
        return Result.success();
    }

    @Override
    public Result getCurrentUserProjects(Integer projectStatus, Integer joinStatus) {
        User currentUser = getUserService.getCurrentUser();


        List<ProjectGroup> projectGroups = selectByUserIdAndProjectStatus(Long.valueOf(currentUser.getCode()), projectStatus, joinStatus);
        //?????????????????????????????????VO
        List<MyProjectVO> projectVOS = new ArrayList<>();
        for (ProjectGroup projectGroup : projectGroups) {
            Integer numberOfSelectedStu = userProjectGroupMapper.selectStuCount(projectGroup.getId(), JoinStatus.JOINED.getValue());
            MyProjectVO myProjectVO = new MyProjectVO();
            Integer status = keyProjectStatusMapper.getStatusByProjectId(projectGroup.getId());


            BeanUtils.copyProperties(projectGroup, myProjectVO);
            myProjectVO.setMemberRole(userProjectGroupMapper.selectByProjectIdAndUserId(projectGroup.getId(), Long.valueOf(currentUser.getCode())).getMemberRole());
            myProjectVO.setId(projectGroup.getId());
            myProjectVO.setNumberOfTheSelected(numberOfSelectedStu);
            myProjectVO.setProjectDetails(getProjectDetails(projectGroup));
            if (status != null) {
                myProjectVO.setStatus(status);
            }
            projectVOS.add(myProjectVO);
        }
        return Result.success(projectVOS);
    }

    private ProjectDetails getProjectDetails(ProjectGroup projectGroup) {
        ProjectDetails projectDetails = new ProjectDetails();
        projectDetails.setLabName(projectGroup.getLabName());
        projectDetails.setAddress(projectGroup.getAddress());
        //???????????????,??????????????????
        User user = userService.selectByUserId(projectGroup.getCreatorId());
        UserProjectGroup userProjectGroup = userProjectService.selectByProjectGroupIdAndUserId(
                projectGroup.getId(),
                Long.valueOf(user.getCode()));
        projectDetails.setCreator(new UserMemberVO(
                Long.valueOf(user.getCode()),
                user.getRealName(),
                userProjectGroup.getMemberRole(), null, null));
        //???????????????????????????
        List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectGroupId(projectGroup.getId());
        List<UserMemberVO> members = new ArrayList<>();
        for (UserProjectGroup userProject : userProjectGroups) {
            User member = userService.selectByUserId(userProject.getUserId());
            //?????????????????????
            if (userProject.getMemberRole().intValue() == MemberRole.PROJECT_GROUP_LEADER.getValue()) {
                projectDetails.setLeader(new UserMemberVO(
                        member.getId(),
                        member.getRealName(),
                        userProject.getMemberRole(), null, null));
            }
            UserMemberVO userMemberVO = new UserMemberVO();
            userMemberVO.setUserId(member.getId());
            userMemberVO.setUserName(member.getRealName());
            userMemberVO.setMemberRole(userProject.getMemberRole());
            members.add(userMemberVO);
        }
        projectDetails.setMembers(members);
        //????????????????????????
        List<Funds> fundsDetails = fundsService.getFundsDetails(projectGroup.getId());
        int applyAmount = 0, agreeAmount = 0;
        for (Funds fundsDetail : fundsDetails) {
            applyAmount += fundsDetail.getAmount();
            if (fundsDetail.getStatus().intValue() == FundsStatus.AGREED.getValue()) {
                agreeAmount += fundsDetail.getAmount();
            }
        }
        projectDetails.setTotalApplyFundsAmount(applyAmount);
        projectDetails.setTotalAgreeFundsAmount(agreeAmount);
        projectDetails.setFundsDetails(fundsDetails);
        List<ProjectFile> projectFiles = projectFileService.getProjectAllFiles(projectGroup.getId());
        projectDetails.setProjectFiles(projectFiles);
        return projectDetails;
    }

    @Override
    public Result agreeJoin(JoinForm[] joinForm) {
        for (JoinForm form : joinForm) {
            User user = userService.selectByUserId(form.getUserId());
            if (user == null) {
                return Result.error(CodeMsg.USER_NO_EXIST);
            }
            ProjectGroup projectGroup = selectByProjectGroupId(form.getProjectGroupId());
            if (projectGroup == null) {
                return Result.error(CodeMsg.PROJECT_GROUP_NOT_EXIST);
            }

            //????????????
            Integer amountOfSelected = userProjectGroupMapper.selectStuCount(projectGroup.getId(), JoinStatus.JOINED.getValue());
            if (amountOfSelected >= projectGroup.getFitPeopleNum()) {
                throw new GlobalException(CodeMsg.PROJECT_USER_MAX_ERROR);
            }

            UserProjectGroup userProjectGroup = userProjectService
                    .selectByProjectGroupIdAndUserId(
                            form.getProjectGroupId(),
                            Long.valueOf(user.getCode()));
            //???????????????????????????
            if (userProjectGroup == null) {
                return Result.error(CodeMsg.USER_NOT_APPLYING);
            }
            //?????????????????????????????????
            if (userProjectGroup.getStatus().intValue() == JoinStatus.JOINED.getValue()) {
                return Result.error(CodeMsg.USER_HAD_JOINED);
            }
            //????????????????????????????????????????????????
            if (userProjectGroup.getStatus().intValue() == JoinStatus.UN_PASS.getValue()) {
                return Result.error(CodeMsg.USER_HAD_BEEN_REJECTED);
            }
            userProjectGroup.setStatus(JoinStatus.JOINED.getValue());
            if (!userProjectService.update(userProjectGroup)) {
                return Result.error(CodeMsg.UPDATE_ERROR);
            }
        }
        return Result.success();
    }

    private Result updateProjectStatus(Long projectGroupId, Integer projectStatus) {
        ProjectGroup projectGroup = selectByProjectGroupId(projectGroupId);
        if (projectGroup == null) {
            throw new GlobalException(CodeMsg.PROJECT_GROUP_NOT_EXIST);
        }
        projectGroup.setStatus(projectStatus);

        //????????????
        if (update(projectGroup)) {
            return Result.success();
        }
        return Result.error(CodeMsg.UPDATE_ERROR);
    }

    /**
     * ????????????
     *
     * @param list
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result agreeEstablish(List<ProjectCheckForm> list) {
        return setProjectStatusAndRecord(list, OperationType.AGREE, OperationUnit.FUNCTIONAL_DEPARTMENT, ProjectStatus.ESTABLISH);
    }

    /**
     * ??????????????????
     * ???????????????
     *
     * @param list
     * @return
     */
    @Override
    public Result agreeIntermediateInspectionProject(List<ProjectCheckForm> list) {
        return setProjectStatusAndRecord(list, OperationType.OFFLINE_CHECK, OperationUnit.FUNCTIONAL_DEPARTMENT, ProjectStatus.ESTABLISH);
    }

    /**
     * ????????????????????????
     *
     * @param list
     * @return
     */
    //TODO ?????????
    @Override
    public Result agreeToBeConcludingProject(List<ProjectCheckForm> list) {
        return setProjectStatusAndRecord(list, OperationType.FUNCTIONAL_PASSED_THE_EXAMINATION, OperationUnit.FUNCTIONAL_DEPARTMENT, ProjectStatus.COLLEGE_FINAL_SUBMISSION);
    }

    /**
     * ??????????????????????????????
     *
     * @param list
     * @return
     */
    @Override
    public Result agreeCollegePassedTheExamination(List<ProjectCheckForm> list) {
        return setProjectStatusAndRecord(list, OperationType.COLLEGE_PASSED_THE_EXAMINATION, OperationUnit.COLLEGE_REVIEWER, ProjectStatus.COLLEGE_FINAL_SUBMISSION);
    }


    /**
     * ????????????
     *
     * @param list
     * @param operationType
     * @param operationUnit
     * @return
     */
    private Result setProjectStatusAndRecord(List<ProjectCheckForm> list, OperationType operationType, OperationUnit operationUnit, ProjectStatus projectStatus) {
        User user = getUserService.getCurrentUser();
        List<OperationRecord> operationRecordS = new LinkedList<>();
        for (ProjectCheckForm projectCheckForm : list) {
            Result result = updateProjectStatus(projectCheckForm.getProjectId(), projectStatus.getValue());
            if (result.getCode() != 0) {
                throw new GlobalException(CodeMsg.UPDATE_ERROR);
            }
            OperationRecord operationRecord = new OperationRecord();

            operationRecord.setOperationType(operationType.getValue());
            operationRecord.setOperationUnit(operationUnit.getValue());
            operationRecord.setOperationReason(projectCheckForm.getReason());
            operationRecord.setRelatedId(projectCheckForm.getProjectId());
            setOperationExecutor(operationRecord);
            operationRecord.setOperationCollege(user.getInstitute());
            operationRecordS.add(operationRecord);
        }
        recordMapper.multiInsert(operationRecordS);
        return Result.success();
    }

    @Override
    public Result getApplyForm(Long projectGroupId) {

        ProjectGroup projectGroup = selectByProjectGroupId(projectGroupId);
        if (projectGroup == null) {
            return Result.error(CodeMsg.PROJECT_GROUP_NOT_EXIST);
        }
        List<User> users = userService.selectProjectJoinedUsers(projectGroupId);
        if (projectGroup.getProjectType().intValue() == ProjectType.KEY.getValue()) {
            ApplyKeyFormInfoVO applyKeyFormInfoVO = convertUtil.addUserDetailVO(users, ApplyKeyFormInfoVO.class);
            applyKeyFormInfoVO.setFundsDetails(fundsService.getFundsDetails(projectGroupId));
            applyKeyFormInfoVO.setCreatorName(userMapper.selectByUserCode(String.valueOf(projectGroup.getCreatorId())).getRealName());
            BeanUtils.copyProperties(projectGroup, applyKeyFormInfoVO);
            applyKeyFormInfoVO.setId(projectGroup.getId());
            return Result.success(applyKeyFormInfoVO);
        } else {
            ApplyGeneralFormInfoVO applyGeneralFormInfoVO = convertUtil.addUserDetailVO(users, ApplyGeneralFormInfoVO.class);
            BeanUtils.copyProperties(projectGroup, applyGeneralFormInfoVO);
            applyGeneralFormInfoVO.setId(projectGroup.getId());
            applyGeneralFormInfoVO.setCreatorName(userMapper.selectByUserCode(String.valueOf(projectGroup.getCreatorId())).getRealName());
            return Result.success(applyGeneralFormInfoVO);
        }
    }

//    @Override
//    public Result appendCreateApply(AppendApplyForm appendApplyForm) {
//        User currentUser = getUserService.getCurrentUser();
//        //??????????????????????????????????????????
//        UserProjectGroup userProjectGroup = userProjectService.selectByProjectGroupIdAndUserId(
//                appendApplyForm.getProjectGroupId(), Long.valueOf(currentUser.getCode());
//        if (userProjectGroup == null) {
//            return Result.error(CodeMsg.USER_NOT_IN_GROUP);
//        }
//
//        if (userProjectGroup.getStatus() < 5){
//            throw new GlobalException(CodeMsg.FUNDS_NOT_EXIST);
//        }
//
//        //????????????????????????????????????
//        if (userProjectGroup.getMemberRole().intValue() == MemberRole.NORMAL_MEMBER.getValue()) {
//            Result.error(CodeMsg.PERMISSION_DENNY);
//        }
//        FundForm[] fundsForms = appendApplyForm.getFundForms();
//        for (FundForm fundsForm : fundsForms) {
//            //??????id???????????????????????????
//            if (fundsForm.getFundsId() != null) {
//                Funds funds = fundsService.selectById(fundsForm.getFundsId());
//
//                if (funds == null) {
//                    return Result.error(CodeMsg.FUNDS_NOT_EXIST);
//                }
//                //??????????????????????????????????????????
//                if (funds.getStatus().intValue() == FundsStatus.AGREED.getValue()) {
//                    return Result.error(CodeMsg.FUNDS_AGREE_CANT_CHANGE);
//                }
//                BeanUtils.copyProperties(fundsForm, funds);
//                funds.setUpdateTime(new Date());
//                if (!fundsService.update(funds)) {
//                    return Result.error(CodeMsg.UPDATE_ERROR);
//                }
//            } else {
//                //??????????????????
//                Funds funds = new Funds();
//                BeanUtils.copyProperties(fundsForm, funds);
//                funds.setProjectGroupId(appendApplyForm.getProjectGroupId());
//                funds.setApplicantId(Long.valueOf(currentUser.getCode());
//                funds.setStatus(FundsStatus.APPLYING.getValue());
//                funds.setCreateTime(new Date());
//                funds.setUpdateTime(new Date());
//                if (!fundsService.insert(funds)) {
//                    return Result.error(CodeMsg.ADD_ERROR);
//                }
//            }
//        }
//        return Result.success();
//    }

    @Override
    public Result getPendingApprovalProjectByLabAdministrator() {
        return getCheckInfo(ProjectStatus.DECLARE);
//        return getNewCheckList(ProjectStatus.DECLARE);
    }

    @Override
    public Result getPendingApprovalProjectBySecondaryUnit() {
//        return getCheckInfo(ProjectStatus.LAB_ALLOWED_AND_REPORTED);
        User user = getUserService.getCurrentUser();
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        // ????????????????????????
        //????????????????????????
        ProjectReview projectReview = projectReviewMapper.selectByCollegeAndType(user.getInstitute(),ProjectType.GENERAL.getValue());
        if(projectReview != null){
            //????????????
            return getReviewInfo2();
        }
        return getNewCheckList(ProjectStatus.LAB_ALLOWED_AND_REPORTED);
    }

    /**
     * ?????????????????????????????????????????? ????????????????????????
     * @return
     */
    @Override
    public Result getPendingApprovalProjectByFunctionalDepartment() {
        return getNewCheckList(ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED);
    }

    @Override
    public Result getConclusionProject() {
//        return getCheckInfo(ProjectStatus.CONCLUDED);
        return getNewCheckList(ProjectStatus.CONCLUDED);
    }

    /**
     * ????????????????????????????????????
     * @return
     */
    @Override
    public Result getAllGeneralProject() {
        User user  = getUserService.getCurrentUser();
        List<CheckProjectVO> list = projectGroupMapper.getAllGeneralByCollege(user.getInstitute());
//        List<CheckProjectVO1> list1 = new LinkedList<>();
        for (CheckProjectVO ProjectDTO :list) {

            ProjectDTO.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(ProjectDTO.getId(),JoinStatus.JOINED.getValue()) );
            ProjectDTO.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),ProjectDTO.getId(),JoinStatus.JOINED.getValue()));

        }
        return Result.success(list);
    }
    /**
     * ?????????????????????????????????
     *
     * @return
     */
    @Override
    public Result getToBeConcludingProject() {
//        return getCheckInfo(ProjectStatus.COLLEGE_FINAL_SUBMISSION);
        return getNewCheckList(ProjectStatus.COLLEGE_FINAL_SUBMISSION);
    }

    /**
     * ??????????????????????????????
     *
     * @return
     */
    @Override
    public Result getFunctionCreateCommonApply() {
        User currentUser = getUserService.getCurrentUser();

        List<CheckProjectVO> checkProjectVOs = selectFunctionCreateCommonApply();
        log.info(checkProjectVOs.size() + "***********");

        for (CheckProjectVO checkProjectVO : checkProjectVOs) {
            List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectGroupId(checkProjectVO.getId());
            checkProjectVO.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(checkProjectVO.getId(), null));
            List<UserMemberVO> guidanceTeachers = new ArrayList<>();
            List<UserMemberVO> memberStudents = new ArrayList<>();
            for (UserProjectGroup userProjectGroup : userProjectGroups) {
                UserMemberVO userMemberVO = new UserMemberVO();
                User user = userService.selectByUserId(userProjectGroup.getUserId());
                userMemberVO.setUserId(Long.valueOf(user.getCode()));
                userMemberVO.setUserName(user.getRealName());
                userMemberVO.setMemberRole(userProjectGroup.getMemberRole());
                //???????????????(????????????)
                switch (userProjectGroup.getMemberRole()) {
                    case 1:
                        guidanceTeachers.add(userMemberVO);
                        break;
                    case 2:
                        checkProjectVO.setGroupLeaderPhone(user.getMobilePhone());
                        memberStudents.add(userMemberVO);
                        break;
                    case 3:
                        memberStudents.add(userMemberVO);
                        break;
                    default:
                        break;
                }
                //???????????????????????????id
                ProjectFile applyProjectFile = projectFileService.getAimNameProjectFile(
                        userProjectGroup.getProjectGroupId(),
                        uploadConfig.getApplyFileName());
                if (applyProjectFile != null) {
                    checkProjectVO.setApplyFileId(applyProjectFile.getId());
                }
                checkProjectVO.setMemberStudents(memberStudents);
                checkProjectVO.setGuidanceTeachers(guidanceTeachers);
            }
        }
        return Result.success(checkProjectVOs);
    }

    /**
     * ?????????????????????
     *
     * @param Keyword
     * @return
     */
    @Override
    public Result selectByKeyword(String Keyword) {
        List<SelectByKeywordProjectVO> projectVOS = (List<SelectByKeywordProjectVO>) redisUtil.get("select" + Keyword);
        if (projectVOS == null || projectVOS.size() == 0) {
            projectVOS = projectGroupMapper.selectByKeyword(Keyword,5);
            if (projectVOS != null && projectVOS.size() != 0) {
                redisUtil.set("select" + Keyword, projectVOS, 300);
                log.info("??????redis");
            }
        }
        return Result.success(projectVOS);
    }

    @Override
    public Result selectConclusionByKeyword(String Keyword) {
        List<SelectByKeywordProjectVO> projectVOS = (List<SelectByKeywordProjectVO>) redisUtil.get("selectConclusion" + Keyword);
        if (projectVOS == null || projectVOS.size() == 0) {
            projectVOS = projectGroupMapper.selectByKeyword(Keyword,7);
            if (projectVOS != null && projectVOS.size() != 0) {
                redisUtil.set("selectConclusion" + Keyword, projectVOS, 300);
                log.info("??????redis");
            }
        }
        return Result.success(projectVOS);
    }

    @Override
    public Result selectKeyProjectByKeyword(SelectByKeywordForm Keyword) {
        List<SelectByKeywordProjectVO> projectVOS = (List<SelectByKeywordProjectVO>) redisUtil.get("selectKey" + Keyword);
        if (projectVOS == null || projectVOS.size() == 0) {
            projectVOS = projectGroupMapper.keyProjectSelectByKeyword(Keyword.getKeyword(),5);
            log.info(projectVOS.toString());
            if (projectVOS != null && projectVOS.size() != 0) {
                redisUtil.set("selectKey" + Keyword, projectVOS, 300);
                log.info("??????redis");
            }
        }
        return Result.success(projectVOS);
    }

    @Override
    public Result selectConclusionKeyProjectByKeyword(SelectByKeywordForm Keyword) {
        List<SelectByKeywordProjectVO> projectVOS = (List<SelectByKeywordProjectVO>) redisUtil.get("selectKeyConclusion" + Keyword);
        if (projectVOS == null || projectVOS.size() == 0) {
            projectVOS = projectGroupMapper.keyProjectSelectByKeyword(Keyword.getKeyword(),7);
            log.info(projectVOS.toString());
            if (projectVOS != null && projectVOS.size() != 0) {
                redisUtil.set("selectKeyConclusion" + Keyword, projectVOS, 300);
                log.info("??????redis");
            }
        }
        return Result.success(projectVOS);
    }

    @Override
    public Result getIntermediateInspectionProject() {
//        return getCheckInfo(ProjectStatus.ESTABLISH);
        return getNewCheckList(ProjectStatus.ESTABLISH);
    }

    @Override
    public Result collegeGetsTheItemsToBeCompleted() {
//        timeLimitService.validTime(TimeLimitType.DECLARE_LIMIT);
        //TODO ????????????????????????
//        return getCheckInfo(ProjectStatus.ESTABLISH);
        return getNewCheckList(ProjectStatus.ESTABLISH);
    }

    /**
     * ?????????????????????????????????
     *
     * @return
     */
    @Override
    public Result collegeGetsTheProjects() {
        //TODO ????????????????????????
//        return getCheckInfo(ProjectStatus.COLLEGE_FINAL_SUBMISSION);
        return getNewCheckList(ProjectStatus.COLLEGE_FINAL_SUBMISSION);
    }

    @Override
    public Result getTheSchoolHasCompletedProject() {
        //TODO ????????????????????????
//        return getCheckInfo(ProjectStatus.CONCLUDED);
        return getNewCheckList(ProjectStatus.CONCLUDED);

    }

    @Override
    public Result getMidTermReturnProject() {
//        return getCheckInfo(ProjectStatus.INTERIM_RETURN_MODIFICATION);
        return getNewCheckList(ProjectStatus.INTERIM_RETURN_MODIFICATION);
    }

    @Override
    public Result getEstablishReturnProject() {
        return getNewCheckList(ProjectStatus.FUNCTIONAL_ESTABLISH_RETURNS);
    }

    @Override
    public Result getCollegeReturnProject() {
//        return getCheckInfo(ProjectStatus.COLLEGE_RETURNS);
        return getNewCheckList(ProjectStatus.COLLEGE_RETURNS);
    }

    @Override
    public Result getFunctionReturnProject() {
//        return getCheckInfo(ProjectStatus.FUNCTIONAL_RETURNS);
        return getNewCheckList(ProjectStatus.FUNCTIONAL_RETURNS);
    }


    private Result getReportInfo(Integer role) {

        User currentUser = getUserService.getCurrentUser();

        Integer projectStatus;
        switch (role) {
            //????????????(????????????)
            case 5:
                projectStatus = ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue();
                break;
            //???????????????
            case 4:
                projectStatus = ProjectStatus.LAB_ALLOWED.getValue();
                break;
            default:
                throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
        }
        //??????????????????????????????
        List<CheckProjectVO> checkProjectVOs = projectGroupMapper.selectApplyOrderByTime(projectStatus, ProjectType.GENERAL.getValue(), currentUser.getInstitute());
        for (CheckProjectVO checkProjectVO : checkProjectVOs) {
            List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectGroupId(checkProjectVO.getId());
            List<UserMemberVO> guidanceTeachers = new ArrayList<>();
            //??????????????????????????????
            for (UserProjectGroup userProjectGroup : userProjectGroups
            ) {
                if (userProjectGroup.getMemberRole().equals(MemberRole.GUIDANCE_TEACHER.getValue())) {
                    UserMemberVO userMemberVO = new UserMemberVO();
                    userMemberVO.setMemberRole(userProjectGroup.getMemberRole());
                    userMemberVO.setUserId(userProjectGroup.getUserId());
                    userMemberVO.setUserName(userMapper.selectByUserCode(String.valueOf(userProjectGroup.getUserId())).getRealName());
                    guidanceTeachers.add(userMemberVO);
                }
            }
            checkProjectVO.setGuidanceTeachers(guidanceTeachers);
            checkProjectVO.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(checkProjectVO.getId(), JoinStatus.JOINED.getValue()));
        }
        return Result.success(checkProjectVOs);
    }

    private Result getNewCheckList(ProjectStatus projectStatus){
        Integer status = projectStatus.getValue();
        Integer projectType = ProjectType.GENERAL.getValue();
        //??????????????????????????????????????????????????????
        if (projectStatus == ProjectStatus.DECLARE) {
            projectType = null;
        }
        User currentUser = getUserService.getCurrentUser();
        if(currentUser == null){
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        if(projectStatus == ProjectStatus.COLLEGE_FINAL_SUBMISSION){
            List<NewCheckProjectVO> checkProjectVOS = projectGroupMapper.selectNewApplyOrderByTime2(status, projectType, currentUser.getInstitute());
            for (NewCheckProjectVO checkProjectVO : checkProjectVOS) {
                checkProjectVO.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(checkProjectVO.getId(), null));
                List<UserVO> userVOS = userProjectService.selectGuideTeacherByGroupId(checkProjectVO.getId());
                checkProjectVO.setGuidanceTeachers(userVOS);
            }
            return Result.success(checkProjectVOS);
        }
        List<NewCheckProjectVO> checkProjectVOS = projectGroupMapper.selectNewApplyOrderByTime(status, projectType, currentUser.getInstitute());
        for (NewCheckProjectVO checkProjectVO : checkProjectVOS) {
            checkProjectVO.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(checkProjectVO.getId(), null));
            List<UserVO> userVOS = userProjectService.selectGuideTeacherByGroupId(checkProjectVO.getId());
            checkProjectVO.setGuidanceTeachers(userVOS);
        }
        log.info(String.valueOf(checkProjectVOS.size()));
        return Result.success(checkProjectVOS);
    }
    private Result getCheckInfo(ProjectStatus projectStatus) {
        Integer status = projectStatus.getValue();
        Integer projectType = ProjectType.GENERAL.getValue();
        //??????????????????????????????????????????????????????
        if (projectStatus == ProjectStatus.DECLARE) {
            projectType = null;
        }

        User currentUser = getUserService.getCurrentUser();

        List<CheckProjectVO> checkProjectVOs = projectGroupMapper.selectApplyOrderByTime(status, projectType, currentUser.getInstitute());
        for (CheckProjectVO checkProjectVO : checkProjectVOs) {
            checkProjectVO.setStatus(status);
            List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectGroupId(checkProjectVO.getId());
            checkProjectVO.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(checkProjectVO.getId(), null));
            List<UserMemberVO> guidanceTeachers = new ArrayList<>();
            List<UserMemberVO> memberStudents = new ArrayList<>();
            for (UserProjectGroup userProjectGroup : userProjectGroups) {
                UserMemberVO userMemberVO = new UserMemberVO();
                User user = userService.selectByUserId(userProjectGroup.getUserId());
                userMemberVO.setUserId(Long.valueOf(user.getCode()));
                userMemberVO.setUserName(user.getRealName());
                userMemberVO.setMemberRole(userProjectGroup.getMemberRole());
                //???????????????(????????????)
                switch (userProjectGroup.getMemberRole()) {
                    case 1:
                        guidanceTeachers.add(userMemberVO);
                        break;
                    case 2:
                        checkProjectVO.setGroupLeaderPhone(user.getMobilePhone());
                        memberStudents.add(userMemberVO);
                        break;
                    case 3:
                        memberStudents.add(userMemberVO);
                        break;
                    default:
                        break;
                }
                //???????????????????????????id
                ProjectFile applyProjectFile = projectFileService.getAimNameProjectFile(
                        userProjectGroup.getProjectGroupId(),
                        uploadConfig.getApplyFileName());
                if (applyProjectFile != null) {
                    checkProjectVO.setApplyFileId(applyProjectFile.getId());
                }
                checkProjectVO.setMemberStudents(memberStudents);
                checkProjectVO.setGuidanceTeachers(guidanceTeachers);
            }
        }
        return Result.success(checkProjectVOs);
    }

    /**
     * ??????????????????
     *
     * @param projectIdList
     * @param status        ?????????????????????
     * @return
     */
    private boolean checkProjectStatus(List<Long> projectIdList, Integer status) {
        int count = projectGroupMapper.selectSpecifiedProjectList(projectIdList, status);
        return count == projectIdList.size();
    }


    @Transactional(rollbackFor = GlobalException.class)
    public Result reportToHigherUnit(List<Long> projectGroupIdList, ProjectStatus
            rightProjectStatus, OperationUnit operationUnit) {
        List<OperationRecord> list = new LinkedList<>();
        if (!checkProjectStatus(projectGroupIdList, rightProjectStatus.getValue())) {
            throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
        }

        User user = getUserService.getCurrentUser();

        //??????????????????
        for (Long projectId : projectGroupIdList
        ) {
            OperationRecord operationRecord = new OperationRecord();

            operationRecord.setRelatedId(projectId);
            operationRecord.setOperationReason(null);
            operationRecord.setOperationUnit(operationUnit.getValue());
            operationRecord.setOperationType(OperationType.REPORT.getValue());
            operationRecord.setOperationCollege(user.getInstitute());
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));

            list.add(operationRecord);
        }

        recordMapper.multiInsert(list);
        ProjectStatus newProjectStatus;
        if (rightProjectStatus == ProjectStatus.LAB_ALLOWED) {
            newProjectStatus = ProjectStatus.LAB_ALLOWED_AND_REPORTED;
            //???????????????????????????
        } else {
            newProjectStatus = ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED;
        }
        projectGroupMapper.updateProjectStatusOfList(projectGroupIdList, newProjectStatus.getValue());
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = GlobalException.class)
    public Result reportToCollegeLeader(List<ProjectCheckForm> formList) {
        //??????????????????
        timeLimitService.validTime(TimeLimitType.LAB_REPORT_LIMIT);

        User user = getUserService.getCurrentUser();
        List<Long> projectGroupIdList = new LinkedList<>();
        for (ProjectCheckForm projectCheckForm : formList) {
            projectGroupIdList.add(projectCheckForm.getProjectId());
        }


        //?????????????????? ??????????????????????????????
        if (!checkProjectStatus(projectGroupIdList, ProjectStatus.LAB_ALLOWED.getValue())) {
            throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
        }
        List<OperationRecord> list = new ArrayList<>();

        //??????????????????
        for (ProjectCheckForm form : formList
        ) {
            //??????????????????????????????
            Integer amount = userProjectGroupMapper.selectStuCount(form.getProjectId(), JoinStatus.JOINED.getValue());
            if (amount < 3 || amount > 6) {
                throw new GlobalException(CodeMsg.PROJECT_FIT_PEOPLE_ERROR);
            }

            OperationRecord operationRecord = new OperationRecord();
            operationRecord.setRelatedId(form.getProjectId());
            operationRecord.setOperationReason(form.getReason());
            operationRecord.setOperationType(OperationType.REPORT.getValue());
            operationRecord.setOperationUnit(OperationUnit.LAB_ADMINISTRATOR.getValue());
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));
            operationRecord.setOperationCollege(user.getInstitute());

            list.add(operationRecord);

//            projectGroupMapper.updateProjectStatus(form.getProjectId(), ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue());
        }
        ProjectReview projectReview = projectReviewMapper.selectByCollegeAndType(user.getInstitute(),ProjectType.GENERAL.getValue());
        if(projectReview != null){
            //????????????
            projectGroupMapper.updateProjectStatusOfList(projectGroupIdList, ProjectStatus.PROJECT_REVIEW.getValue());
        }else{
            projectGroupMapper.updateProjectStatusOfList(projectGroupIdList, ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue());
        }


        //????????????????????????
        recordMapper.multiInsert(list);

        return Result.success();
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result reportToFunctionalDepartment(List<Long> projectGroupIdList) {
        //????????????
        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        if (college == null) {
            throw new GlobalException(CodeMsg.COLLEGE_TYPE_NULL_ERROR);
        }

        //?????????????????? ??????
//        for (Long id : projectGroupIdList) {
//            String serialNumber = projectGroupMapper.selectByPrimaryKey(id).getSerialNumber();
//            //??????????????????????????????????????????
//            projectGroupMapper.updateProjectSerialNumber(id, SerialNumberUtil.getSerialNumberOfProject(college, ProjectType.GENERAL.getValue(), serialNumber));
//        }


        AmountAndTypeVO amountAndTypeVO = amountLimitMapper.getAmountAndTypeVOByCollegeAndProjectType(college, ProjectType.GENERAL.getValue(), RoleType.SECONDARY_UNIT.getValue());
        Integer currentAmount = projectGroupMapper.getCountOfSpecifiedStatusAndProjectProject(ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED.getValue(), college);
        if (currentAmount + projectGroupIdList.size() > amountAndTypeVO.getMaxAmount()) {
            throw new GlobalException(CodeMsg.PROJECT_AMOUNT_LIMIT);
        }

        //????????????
//        timeLimitService.validTime(TimeLimitType.SECONDARY_UNIT_REPORT_LIMIT);
        return reportToHigherUnit(projectGroupIdList, ProjectStatus.SECONDARY_UNIT_ALLOWED, OperationUnit.SECONDARY_UNIT);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result ensureOrNotModify(ConfirmForm confirmForm) {
        Integer result = confirmForm.getResult();
        Long projectId = confirmForm.getProjectId();
        //????????????
        if (recordMapper.selectDesignatedTypeListByRelatedIdAndType
                (OperationType.AGREE.getValue(), projectId).size() == 0) {
            throw new GlobalException(CodeMsg.PROJECT_NOT_MODIFY_BY_FUNCTION_DEPARTMENT);
        }
        //??????????????????
        if (result.equals(OperationType.AGREE.getValue())) {
            updateProjectStatus(projectId, ProjectStatus.ESTABLISH.getValue());
        } else if (result.equals(OperationType.REJECT.getValue())) {
            updateProjectStatus(projectId, ProjectStatus.ESTABLISH_FAILED.getValue());
        }
        return Result.success();
    }

    @Override
    public Result getProjectDetailById(Long projectId) {
        List<ProjectHistoryInfo> list = recordMapper.selectAllByProjectId(projectId);
        return Result.success(list);
    }

    @Override
    public Result approveProjectApplyByLabAdministrator(List<ProjectCheckForm> list) {
        //??????????????????
        timeLimitService.validTime(TimeLimitType.LAB_CHECK_LIMIT);
        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        if (college == null) {
            throw new GlobalException(CodeMsg.PARAM_CANT_BE_NULL);
        }
        //????????????????????????
        //????????????????????????
//        ProjectReview projectReview = projectReviewMapper.selectByCollegeAndType(college,ProjectType.GENERAL.getValue());
//        if(projectReview != null){
//            //????????????
//            return approveProjectReview(list, RoleType.SECONDARY_UNIT.getValue());
//        }
        return approveProjectApply(list, RoleType.LAB_ADMINISTRATOR.getValue());
    }


    /**
     * ??????????????????????????????
     * @param list
     * @return
     */
    @Override
    public Result approveProjectApplyBySecondaryUnit(List<ProjectCheckForm> list) {
        //??????????????????
        timeLimitService.validTime(TimeLimitType.SECONDARY_UNIT_CHECK_LIMIT);


        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        if (college == null) {
            throw new GlobalException(CodeMsg.PARAM_CANT_BE_NULL);
        }
//        //????????????????????????
//        //????????????????????????
//        ProjectReview projectReview = projectReviewMapper.selectByCollegeAndType(college,ProjectType.GENERAL.getValue());
//        if(projectReview != null){
//            //????????????
//            return approveProjectReview(list, RoleType.SECONDARY_UNIT.getValue());
//        }
        return approveProjectApply(list, RoleType.SECONDARY_UNIT.getValue());
    }

    @Override
    public Result collegeSetScore(List<CollegeGiveScore> collegeGiveScores) {

        return getResult(collegeGiveScores, projectReviewResultMapper);
    }

    Result getResult(List<CollegeGiveScore> collegeGiveScores, ProjectReviewResultMapper projectReviewResultMapper) {
        User user = getUserService.getCurrentUser();
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
            //????????????
            Result result = approveProjectNormal(giveScore.getProjectId(),RoleType.COLLEGE_FINALIZATION_REVIEW.getValue());
            if(result.getCode()!=0){
                throw new GlobalException(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
            projectReviewResultMapper.insert(reviewResult);
        }
        return Result.success();
    }

    /**
     * ????????????????????????
     * @param projectId
     * @param role
     * @return
     */
    @Transactional(rollbackFor = GlobalException.class)
    Result approveProjectNormal(Long projectId, Integer role) {
        User user = getUserService.getCurrentUser();
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        Integer operationUnit = OperationUnit.COLLEGE_REVIEWER.getValue();
        //????????????
        Integer projectStatus = ProjectStatus.PROJECT_REVIEW.getValue();
        //???????????????????????????
        Integer updateProjectStatus = ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue();


            //????????????????????????????????????,??????????????????
            ProjectGroup projectGroup = selectByProjectGroupId(projectId);
            //?????????????????????????????????,????????????
            if (role.equals(RoleType.COLLEGE_REVIEW_TEACHER.getValue())) {
                if (!projectGroup.getStatus().equals(projectStatus)) {
                    throw new GlobalException("???????????????" + projectGroup.getId() + "???????????????????????????", CodeMsg.PROJECT_CURRENT_STATUS_ERROR.getCode());
                }

            }//?????????????????????????????????????????????
            updateProjectStatus(projectId, updateProjectStatus);


        return Result.success();
    }
    /**
     * ??????????????????????????????????????????
     * @param formList
     * @param role
     * @return
     */
    @Transactional(rollbackFor = GlobalException.class)
    public Result approveProjectReview(List<ProjectCheckForm> formList, Integer role) {
        User user = getUserService.getCurrentUser();
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        Integer operationUnit = OperationUnit.SECONDARY_UNIT.getValue();
        //????????????
        Integer projectStatus = ProjectStatus.LAB_ALLOWED.getValue();
        //???????????????????????????
        Integer updateProjectStatus = ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue();

        List<OperationRecord> list = new LinkedList<>();
        for (ProjectCheckForm form : formList
        ) {
            OperationRecord operationRecord = new OperationRecord();

            operationRecord.setRelatedId(form.getProjectId());
            operationRecord.setOperationReason(form.getReason());
            operationRecord.setOperationType(OperationType.AGREE.getValue());
            operationRecord.setOperationUnit(operationUnit);
            operationRecord.setOperationCollege(user.getInstitute());
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));


            list.add(operationRecord);
            //????????????????????????????????????,??????????????????
            ProjectGroup projectGroup = selectByProjectGroupId(form.getProjectId());
            //?????????????????????????????????,????????????
            if (role.equals(RoleType.SECONDARY_UNIT.getValue())) {
                if (!projectGroup.getStatus().equals(projectStatus)) {
                    throw new GlobalException("???????????????" + projectGroup.getId() + "???????????????????????????????????????", CodeMsg.PROJECT_CURRENT_STATUS_ERROR.getCode());
                }
            }
            //?????????????????????????????????????????????
            updateProjectStatus(form.getProjectId(), updateProjectStatus);

        }
        recordMapper.multiInsert(list);
        return Result.success();
    }


    /**
     * ????????????
     *
     * @param list
     * @return
     */
    @Override
    public Result midTermReviewPassed(List<ProjectCheckForm> list) {
        return setProjectStatusAndRecord(list, OperationType.MIDTERM_REVIEW_PASSED, OperationUnit.FUNCTIONAL_DEPARTMENT, ProjectStatus.ESTABLISH);
    }

    /**
     * ????????????????????????
     * @param list
     * @return
     */
    @Override
    public Result establishReviewPassed(List<ProjectCheckForm> list) {
        return setProjectStatusAndRecord(list, OperationType.FUNCTIONAL_ESTABLISH_PASSED, OperationUnit.FUNCTIONAL_DEPARTMENT, ProjectStatus.ESTABLISH);
    }

    @Override
    public Result CollegeReviewPassed(List<ProjectGrade> projectGradeList) {
        User user = getUserService.getCurrentUser();
        //????????????
        if (!userRoleService.validContainsUserRole(RoleType.COLLEGE_FINALIZATION_REVIEW)) {
            throw new GlobalException(CodeMsg.PERMISSION_DENNY);
        }
        List<ProjectCheckForm> list = new LinkedList<>();
        for (ProjectGrade projectGrade : projectGradeList) {
            ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectGrade.getProjectId());
            if (!projectGroup.getStatus().equals(ProjectStatus.COLLEGE_RETURNS.getValue())) {
                throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
            }
            ProjectCheckForm projectCheckForm = new ProjectCheckForm();
            BeanUtils.copyProperties(projectGrade, projectCheckForm);
            projectCheckForm.setReason("????????????????????????,?????????"+GeneralGrade.getTips(projectGrade.getValue()));
            list.add(projectCheckForm);
        }
        //????????????
        setProjectGrade(projectGradeList, user, 1);
        return setProjectStatusAndRecord(list, OperationType.COLLEGE_PASSED_THE_EXAMINATION, OperationUnit.COLLEGE_REVIEWER, ProjectStatus.COLLEGE_FINAL_SUBMISSION);
    }

    @Override
    public Result functionReviewPassed(List<ProjectCheckForm> list) {
        return setProjectStatusAndRecord(list, OperationType.FUNCTIONAL_REVIEW_PASSED, OperationUnit.FUNCTIONAL_DEPARTMENT, ProjectStatus.CONCLUDED);
    }


    @Override
    public Result conditionallyQueryOfProject(QueryConditionForm form) {
        return conditionallyQueryOfCheckedProject(form);
    }

    //??????????????????
    @Override
    public Result getHistoricalProjectInfo(HistoryQueryProjectInfo info) {
        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        //?????????????????????????????????
        if (info.getOperationUnit().equals(OperationUnit.FUNCTIONAL_DEPARTMENT.getValue())) {
            college = null;
        }

        List<ProjectGroup> list;

        // 2?????????    1??????3??????
        //???????????????????????????  ??????????????????????????????
        /**
         * 4  1?????? ??????????????????????????????
         * 4  3?????? ???????????????????????????
         * 5  1?????? ????????????????????????,?????????
         * 5  3?????? ????????????????????????????????????
         * 6  1 ??????6  3???????????? ???????????????????????? ????????????
         * ??????
         * 42 52 ????????????????????????????????????
         *
         * 62????????????
         *
         *  @ApiModelProperty("???????????? 4, ??????????????????5,????????????(????????????)???6,????????????")
         *     private Integer operationUnit;
         *
         *     /**
         *      * {@link com.swpu.uchain.openexperiment.enums.OperationType}
         *      */
         //@ApiModelProperty("??????????????? 2,??????1|3,??????")private Integer operationType;

        if (info.getOperationType().equals(OperationType.AGREE.getValue())
                || info.getOperationType().equals(OperationType.REPORT.getValue())) {
            //?????????????????????
            Integer status = 0;
            if (info.getOperationUnit().equals(OperationUnit.LAB_ADMINISTRATOR.getValue())) {
                if (info.getOperationType().equals(OperationType.AGREE.getValue())) {
                    status = ProjectStatus.LAB_ALLOWED.getValue();
                } else {
                    status = ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue();
                }
            } else if (info.getOperationUnit().equals(OperationUnit.SECONDARY_UNIT.getValue())) {
                if (info.getOperationType().equals(OperationType.AGREE.getValue())) {
                    status = ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue();
                } else {
                    status = ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED.getValue();
                }
            } else if (info.getOperationUnit().equals(OperationUnit.FUNCTIONAL_DEPARTMENT.getValue())) {
                college = null;
                //?????????????????????????????????????????????????????????????????????????????????-3????????????????????????
                status = ProjectStatus.ESTABLISH.getValue();
            }
            list = projectGroupMapper.selectGeneralPassedProjectList(college, status);
        } else {
            if(user.getInstitute() != 39) {
                list = projectGroupMapper.selectGeneralRejectedProjectList(college);
            }else {
                list = projectGroupMapper.selectGeneralRejectedProjectList(null);
            }
        }

        for (ProjectGroup projectGroup : list
        ) {
            projectGroup.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(projectGroup.getId(), JoinStatus.JOINED.getValue()));
            projectGroup.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(), projectGroup.getId(), JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }

    @Override
    public Result getAllOpenTopicByCondition(QueryConditionForm form) {
        //???????????????????????????ID????????????????????????
        List<Long> projectIdList = projectGroupMapper.conditionQuery(form);
        if (projectIdList.isEmpty()) {
            return Result.success(null);
        }
        //????????????????????????
        List<OpenTopicInfo> list = projectGroupMapper.getAllOpenTopic(projectIdList);
        for (OpenTopicInfo info : list
        ) {
            info.setAmountOfSelected(userProjectGroupMapper.selectStuCount(info.getId(), JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }

    private Result conditionallyQueryOfCheckedProject(QueryConditionForm form) {
        List<Long> projectIdList = projectGroupMapper.conditionQuery(form);
        if (projectIdList.isEmpty()) {
            return Result.success(null);
        }
        List<ProjectGroup> list = projectGroupMapper.selectAllByList(projectIdList);
        for (ProjectGroup projectGroup : list
        ) {
            Long id = projectGroup.getId();
            projectGroup.setNumberOfTheSelected(userProjectGroupMapper.getMemberAmountOfProject(id, null));
            projectGroup.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(), id, JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }

    @Override
    public Result getToBeReportedProjectByLabLeader() {
        User user = getUserService.getCurrentUser();
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        return getReportInfo(RoleType.LAB_ADMINISTRATOR.getValue());
    }

    @Override
    public Result getToBeReportedProjectBySecondaryUnit() {
        User user = getUserService.getCurrentUser();
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        // ????????????????????????
        //????????????????????????
        ProjectReview projectReview = projectReviewMapper.selectByCollegeAndType(user.getInstitute(),ProjectType.GENERAL.getValue());
        if(projectReview != null){
            //????????????
            return getReviewInfo();
        }
        return getReportInfo(RoleType.SECONDARY_UNIT.getValue());
    }
    private Result getReviewInfo2() {

        User currentUser = getUserService.getCurrentUser();

        //??????????????????????????????
        List<ProjectReviewVO> projectReviewVOS = projectGroupMapper.selectHasReview(currentUser.getInstitute(),ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue());
        for (ProjectReviewVO projectReviewVO : projectReviewVOS) {
//            //??????????????????????????????
//            for (UserProjectGroup userProjectGroup : userProjectGroups
//            ) {
//                if (userProjectGroup.getMemberRole().equals(MemberRole.GUIDANCE_TEACHER.getValue())) {
//                    UserMemberVO userMemberVO = new UserMemberVO();
//                    userMemberVO.setMemberRole(userProjectGroup.getMemberRole());
//                    userMemberVO.setUserId(userProjectGroup.getUserId());
//                    userMemberVO.setUserName(userMapper.selectByUserCode(String.valueOf(userProjectGroup.getUserId())).getRealName());
//                    guidanceTeachers.add(userMemberVO);
//                }
//            }
            //??????????????????????????????
            projectReviewVO.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),projectReviewVO.getId(),JoinStatus.JOINED.getValue()));
            projectReviewVO.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(projectReviewVO.getId(), JoinStatus.JOINED.getValue()));
        }


        return Result.success( SortListUtil.sort(projectReviewVOS,"score",SortListUtil.DESC));
    }


    private Result getReviewInfo() {

        User currentUser = getUserService.getCurrentUser();

        //??????????????????????????????
        List<ProjectReviewVO> projectReviewVOS = projectGroupMapper.selectHasReview(currentUser.getInstitute(),ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue());
        for (ProjectReviewVO projectReviewVO : projectReviewVOS) {
//            for (UserProjectGroup userProjectGroup : userProjectGroups
//            ) {
//                if (userProjectGroup.getMemberRole().equals(MemberRole.GUIDANCE_TEACHER.getValue())) {
//                    UserMemberVO userMemberVO = new UserMemberVO();
//                    userMemberVO.setMemberRole(userProjectGroup.getMemberRole());
//                    userMemberVO.setUserId(userProjectGroup.getUserId());
//                    userMemberVO.setUserName(userMapper.selectByUserCode(String.valueOf(userProjectGroup.getUserId())).getRealName());
//                    guidanceTeachers.add(userMemberVO);
//                }
//            }
            //??????????????????????????????
            projectReviewVO.setGuidanceTeachers(userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(),projectReviewVO.getId(),JoinStatus.JOINED.getValue()));
            projectReviewVO.setNumberOfTheSelected(userProjectGroupMapper.selectStuCount(projectReviewVO.getId(), JoinStatus.JOINED.getValue()));
        }


        return Result.success( SortListUtil.sort(projectReviewVOS,"score",SortListUtil.DESC));
    }

    @Override
    public Result getToReviewProject() {
        User user = getUserService.getCurrentUser();
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        return getNewCheckList(ProjectStatus.PROJECT_REVIEW);
    }


    @Transactional(rollbackFor = GlobalException.class)
    public Result approveProjectApply(List<ProjectCheckForm> formList, Integer role) {
        User user = getUserService.getCurrentUser();
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        Integer operationUnit;
        //????????????
        Integer projectStatus = null;
        //???????????????????????????
        Integer updateProjectStatus = null;
        switch (role) {
            //????????????????????????
            case 4:
                operationUnit = OperationUnit.LAB_ADMINISTRATOR.getValue();
                projectStatus = ProjectStatus.DECLARE.getValue();
                updateProjectStatus = ProjectStatus.LAB_ALLOWED.getValue();
                break;
            //?????????????????????
            case 5:
                operationUnit = OperationUnit.SECONDARY_UNIT.getValue();
                projectStatus = ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue();
                updateProjectStatus = ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue();
                break;
            default:
                //??????????????????
                operationUnit = -5;
        }
        List<OperationRecord> list = new LinkedList<>();
        for (ProjectCheckForm form : formList
        ) {
            OperationRecord operationRecord = new OperationRecord();

            operationRecord.setRelatedId(form.getProjectId());
            operationRecord.setOperationReason(form.getReason());
            operationRecord.setOperationType(OperationType.AGREE.getValue());
            operationRecord.setOperationUnit(operationUnit);
            operationRecord.setOperationCollege(user.getInstitute());
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));


            list.add(operationRecord);
            //????????????????????????????????????,??????????????????
            ProjectGroup projectGroup = selectByProjectGroupId(form.getProjectId());
            if (role == 4 && !projectGroup.getStatus().equals(projectStatus)) {
                throw new GlobalException("???????????????" + projectGroup.getId() + "????????????????????????", CodeMsg.PROJECT_STATUS_IS_NOT_DECLARE.getCode());
            }
            //?????????????????????????????????,????????????
            if (role.equals(RoleType.SECONDARY_UNIT.getValue())) {
                if (!projectGroup.getStatus().equals(projectStatus)) {
                    throw new GlobalException("???????????????" + projectGroup.getId() + "?????????????????????????????????", CodeMsg.PROJECT_CURRENT_STATUS_ERROR.getCode());
                }
            }
            //?????????????????????????????????????????????
            updateProjectStatus(form.getProjectId(), updateProjectStatus);

        }
        recordMapper.multiInsert(list);
        return Result.success();
    }

    /**
     * ?????????
     * ????????????????????????????????????
     *
     * @return
     */
    @Override
    public Result getAllOpenTopic() {
        //??????  ??????????????????????????????????????????????????????

        User currentUser = getUserService.getCurrentUser();
        Integer college = currentUser.getInstitute();
        TimeLimit timeLimit = timeLimitService.getTimeLimitByTypeAndCollege(TimeLimitType.JOIN_APPLY_LIMIT, college);
        //?????????????????????
        log.error(timeLimit.getEndTime()+"  "+timeLimit.getStartTime() + "   "+ new Date());
        if (timeLimit.getEndTime().before(new Date()) || timeLimit.getStartTime().after(new Date())) {
            //???????????????????????????
            if (userRoleService.validContainsUserRole(RoleType.NORMAL_STU)) {
                return Result.success();
            }
        }

        List<OpenTopicInfo> list = projectGroupMapper.getAllOpenTopic(null);
        for (OpenTopicInfo info : list
        ) {
            info.setAmountOfSelected(userProjectGroupMapper.selectStuCount(info.getId(), JoinStatus.JOINED.getValue()));
        }
        return Result.success(list);
    }

    @Override
    public List getJoinInfo() {
        User currentUser = getUserService.getCurrentUser();
        //???????????????????????????--???????????????
        if (currentUser == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }

        Role role = roleMapper.selectByUserId(Long.valueOf(currentUser.getCode()));
        if (role.getId() < (RoleType.MENTOR.getValue()).longValue()) {
            throw new GlobalException(CodeMsg.PERMISSION_DENNY);
        }
        List<JoinUnCheckVO> joinUnCheckVOS = new ArrayList<>();

        //??????????????????????????????????????????
        List<ProjectGroup> projectGroups = selectByUserIdAndProjectStatus(Long.valueOf(currentUser.getCode()), null, JoinStatus.JOINED.getValue());
        for (int i = 0; i < projectGroups.size(); i++) {
            if (projectGroups.get(i) == null) {
                i++;
            }
            if (projectGroups.get(i).getIsOpenTopic().equals(OpenTopicType.NOT_OPEN_TOPIC.getValue())) {
                if (i != projectGroups.size() - 1) {
                    i++;
                }
            }
            ProjectGroup projectGroup = projectGroups.get(i);
            List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectAndStatus(projectGroup.getId(), null);
            for (UserProjectGroup userProjectGroup : userProjectGroups) {
                JoinUnCheckVO joinUnCheckVO = getJoinUnCheckVO(userProjectGroup, projectGroup);
                joinUnCheckVOS.add(joinUnCheckVO);
            }
        }
        return joinUnCheckVOS;
    }

    @Override
    public Result getApplyingJoinInfoByCondition(MemberQueryCondition condition) {
        User currentUser = getUserService.getCurrentUser();
        //????????????
        if (currentUser == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        if (condition == null) {
            throw new GlobalException(CodeMsg.PARAM_CANT_BE_NULL);
        }
        List<JoinUnCheckVO> joinUnCheckVOS = new ArrayList<>();
        if (condition.getId() == null) {
            List<ProjectGroup> projectGroups = selectByUserIdAndProjectStatus(Long.valueOf(currentUser.getCode()), ProjectStatus.LAB_ALLOWED.getValue(), JoinStatus.JOINED.getValue());
            for (int i = 0; i < projectGroups.size(); i++) {
                if (projectGroups.get(i) == null) {
                    break;
                }
                if (projectGroups.get(i).getIsOpenTopic().equals(OpenTopicType.NOT_OPEN_TOPIC.getValue())) {
//                    if(i!=projectGroups.size()){
                        i++;
//                    }
                }
                log.error(String.valueOf(i));
                ProjectGroup projectGroup = projectGroups.get(i);
                List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectAndStatus(projectGroup.getId(), condition.getStatus());
                for (UserProjectGroup userProjectGroup : userProjectGroups) {
                    JoinUnCheckVO joinUnCheckVO = getJoinUnCheckVO(userProjectGroup, projectGroup);
                    joinUnCheckVOS.add(joinUnCheckVO);
                }
            }
            //???????????????????????????
        } else {
            List<UserProjectGroup> userProjectGroups = userProjectService.selectByProjectAndStatus(condition.getId(), condition.getStatus());
            ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(condition.getId());
            for (UserProjectGroup userProjectGroup : userProjectGroups) {
                JoinUnCheckVO joinUnCheckVO = getJoinUnCheckVO(userProjectGroup, projectGroup);
                joinUnCheckVOS.add(joinUnCheckVO);
            }
        }
        return Result.success(joinUnCheckVOS);
    }

    /**
     * ????????????????????????
     *
     * @param joinForm
     * @return
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Result addStudentToProject(JoinForm joinForm) {
        User user = getUserService.getCurrentUser();
        Long userId = Long.valueOf(user.getCode());

        //?????????????????????????????? ????????????
        //User addUser = userMapper.selectByUserCode(joinForm.getUserId().toString());
        //if (addUser.getMobilePhone() == null) {
          //  throw new GlobalException(CodeMsg.ADD_USER_INFO_NOT_COMPLETE);
      //  }



        ProjectGroup projectGroup = selectByProjectGroupId(joinForm.getProjectGroupId());
        Integer status = projectGroup.getStatus();
        Integer keyStatus = keyProjectStatusMapper.getStatusByProjectId(joinForm.getProjectGroupId());
        if(keyStatus == null) {
            //??????????????????
            if (!status.equals(ProjectStatus.LAB_ALLOWED.getValue()) && !status.equals(ProjectStatus.REJECT_MODIFY.getValue()) && !status.equals(ProjectStatus.GUIDE_TEACHER_ALLOWED.getValue())
            ) {
                int SubordinateCollege = projectGroupMapper.selectSubordinateCollege(joinForm.getProjectGroupId());
                if (SubordinateCollege != 39) {
                    throw new GlobalException(CodeMsg.CURRENT_PROJECT_STATUS_ERROR);
                }
            }
        }else{
            if (!keyStatus.equals(ProjectStatus.LAB_ALLOWED.getValue()) && !keyStatus.equals(ProjectStatus.REJECT_MODIFY.getValue()) && !keyStatus.equals(ProjectStatus.GUIDE_TEACHER_ALLOWED.getValue())
            ) {
                int SubordinateCollege = projectGroupMapper.selectSubordinateCollege(joinForm.getProjectGroupId());
                if (SubordinateCollege != 39) {
                    throw new GlobalException(CodeMsg.CURRENT_PROJECT_STATUS_ERROR);
                }
            }
        }

        //????????????????????????????????????
        UserProjectAccount userProjectAccount2 = userProjectAccountMapper.selectByCode(String.valueOf(joinForm.getUserId()));
        //?????????????????????
        if(userProjectAccount2 != null) {
            if ((userProjectAccount2.getKeyNum() + userProjectAccount2.getGeneralNum()) >= 3) {
                throw new GlobalException(CodeMsg.STU_MAX_NUM_OF_TYPE);
            }else{
                if(projectGroup.getProjectType().equals(ProjectType.GENERAL.getValue())){
                    userProjectAccount2.setGeneralNum(userProjectAccount2.getGeneralNum()+1);
                }else {
                    userProjectAccount2.setKeyNum(userProjectAccount2.getKeyNum()+1);
                }
                userProjectAccountMapper.updateByPrimaryKey(userProjectAccount2);
            }
            //?????????
        }else{
            UserProjectAccount userAccount = new UserProjectAccount();
            userAccount.setCode(String.valueOf(joinForm.getUserId()));
            userAccount.setCollege(String.valueOf(userMapper.selectByUserCode(String.valueOf(joinForm.getUserId())).getInstitute()));
            userAccount.setUserType(1);
            if(projectGroup.getProjectType().equals(ProjectType.GENERAL.getValue())){
                userAccount.setGeneralNum(1);
                userAccount.setKeyNum(0);
            }else {
                userAccount.setGeneralNum(0);
                userAccount.setKeyNum(1);
            }

            userProjectAccountMapper.insert(userAccount);
        }

        UserProjectGroup userProjectGroupOfCurrentUser = userProjectGroupMapper.selectByProjectGroupIdAndUserId(joinForm.getProjectGroupId(), userId);
        if (userProjectGroupOfCurrentUser == null || !userProjectGroupOfCurrentUser.getMemberRole().equals(MemberRole.GUIDANCE_TEACHER.getValue())) {
            int SubordinateCollege = projectGroupMapper.selectSubordinateCollege(joinForm.getProjectGroupId());
            if (SubordinateCollege != 39) {
                throw new GlobalException(CodeMsg.USER_NOT_IN_GROUP);
            }
        }

        //????????????????????????
        if (userMapper.selectByUserCode(String.valueOf(joinForm.getUserId())) == null) {
            throw new GlobalException(CodeMsg.USER_NO_EXIST);
        }

        //??????????????????????????????
        if (userProjectGroupMapper.selectByProjectGroupIdAndUserId(joinForm.getProjectGroupId(), joinForm.getUserId()) != null) {
            throw new GlobalException(CodeMsg.USER_HAD_JOINED);
        }

        //??????????????????????????????
        Integer amount = userProjectGroupMapper.selectStuCount(joinForm.getProjectGroupId(), JoinStatus.JOINED.getValue());

        //??????????????????
        if (projectGroup.getFitPeopleNum() <= amount) {
            throw new GlobalException(CodeMsg.PROJECT_USER_MAX_ERROR);
        }

        UserProjectGroup userProjectGroup = new UserProjectGroup();
        userProjectGroup.setUserId(joinForm.getUserId());
        userProjectGroup.setProjectGroupId(joinForm.getProjectGroupId());
        userProjectGroup.setMemberRole(MemberRole.NORMAL_MEMBER.getValue());
        userProjectGroup.setStatus(JoinStatus.JOINED.getValue());
        userProjectGroup.setJoinTime(new Date());
        userProjectGroupMapper.insert(userProjectGroup);
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Result removeStudentFromProject(JoinForm joinForm) {
        //??????????????????
        Integer status = projectGroupMapper.selectByPrimaryKey(joinForm.getProjectGroupId()).getStatus();
        if (!status.equals(ProjectStatus.LAB_ALLOWED.getValue()) && !status.equals(ProjectStatus.REJECT_MODIFY.getValue())
                && !status.equals(ProjectStatus.DECLARE.getValue())
        ) {
            throw new GlobalException(CodeMsg.CURRENT_PROJECT_STATUS_ERROR);
        }

        //??????????????????
        Integer amount = userProjectGroupMapper.selectStuCount(joinForm.getProjectGroupId(), JoinStatus.JOINED.getValue());
        ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(joinForm.getProjectGroupId());
        if (amount <= projectGroup.getFitPeopleNum()) {
            throw new GlobalException(CodeMsg.FIT_PEOPLE_LIMIT_ERROR);
        }



        User user = getUserService.getCurrentUser();
        Long userId = Long.valueOf(user.getCode());
        UserProjectGroup userProjectGroupOfCurrentUser = userProjectGroupMapper.selectByProjectGroupIdAndUserId(joinForm.getProjectGroupId(), userId);
        if (userProjectGroupOfCurrentUser == null || !userProjectGroupOfCurrentUser.getMemberRole().equals(MemberRole.PROJECT_GROUP_LEADER.getValue())) {
            throw new GlobalException(CodeMsg.USER_NOT_IN_GROUP);
        }
        UserProjectGroup userProjectGroupOfJoinUser = userProjectGroupMapper.selectByProjectGroupIdAndUserId(joinForm.getProjectGroupId(), joinForm.getUserId());
        if (userProjectGroupOfJoinUser == null) {
            throw new GlobalException(CodeMsg.USER_HAD_JOINED_CANT_REJECT);
        }
        //??????????????????
        UserProjectAccount userProjectAccount2 = userProjectAccountMapper.selectByCode(user.getCode());
        //?????????????????????
        if(userProjectAccount2 != null) {
            if (projectGroup.getProjectType().equals(ProjectType.GENERAL.getValue())) {
                userProjectAccount2.setGeneralNum(userProjectAccount2.getGeneralNum() - 1);
            } else {
                userProjectAccount2.setKeyNum(userProjectAccount2.getKeyNum() - 1);
            }
            userProjectAccountMapper.updateByPrimaryKey(userProjectAccount2);
        }
        userProjectGroupMapper.deleteByPrimaryKey(userProjectGroupOfCurrentUser.getId());
        return Result.success();
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Result cancelStudentFromProject(GenericId joinForm) {
        User user = getUserService.getCurrentUser();
        UserProjectGroup userProjectGroup = userProjectGroupMapper.selectByProjectGroupIdAndUserId(joinForm.getId(), Long.valueOf(user.getCode()));
        if(userProjectGroup.getStatus() == 2){
            throw new GlobalException(CodeMsg.CANT_REMOVE);
        }
        ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(joinForm.getId());
        //??????????????????
        UserProjectAccount userProjectAccount2 = userProjectAccountMapper.selectByCode(user.getCode());
        //?????????????????????
        if(userProjectAccount2 != null) {
            if (projectGroup.getProjectType().equals(ProjectType.GENERAL.getValue())) {
                userProjectAccount2.setGeneralNum(userProjectAccount2.getGeneralNum() - 1);
            } else {
                userProjectAccount2.setKeyNum(userProjectAccount2.getKeyNum() - 1);
            }
            userProjectAccountMapper.updateByPrimaryKey(userProjectAccount2);
        }
        userProjectGroupMapper.deleteByPrimaryKey(userProjectGroup.getId());
        return Result.success();
    }

    private JoinUnCheckVO getJoinUnCheckVO(UserProjectGroup userProjectGroup, ProjectGroup projectGroup) {
        User user = userService.selectByUserId(userProjectGroup.getUserId());
        JoinUnCheckVO joinUnCheckVO = new JoinUnCheckVO();
        joinUnCheckVO.setId(projectGroup.getId());
        joinUnCheckVO.setMemberRole(userProjectGroup.getMemberRole());
        joinUnCheckVO.setSerialNumber(projectGroup.getSerialNumber());
        joinUnCheckVO.setProjectName(projectGroup.getProjectName());
        joinUnCheckVO.setProjectType(projectGroup.getProjectType());
        joinUnCheckVO.setPersonJudge(userProjectGroup.getPersonalJudge());
        joinUnCheckVO.setTechnicalRole(userProjectGroup.getTechnicalRole());
        joinUnCheckVO.setApplyTime(userProjectGroup.getJoinTime());
        joinUnCheckVO.setExperimentType(projectGroup.getExperimentType());
        joinUnCheckVO.setUserDetailVO(convertUtil.convertUserDetailVO(user));
        joinUnCheckVO.setStatus(userProjectGroup.getStatus());

        return joinUnCheckVO;
    }

    @Override
    public Result rejectJoin(JoinForm[] joinForm) {
        for (JoinForm form : joinForm) {
            UserProjectGroup userProjectGroup = userProjectService.selectByProjectGroupIdAndUserId(form.getProjectGroupId(), form.getUserId());
            if (userProjectGroup == null) {
                return Result.error(CodeMsg.USER_NOT_APPLYING);
            }
            if (userProjectGroup.getStatus() != JoinStatus.APPLYING.getValue().intValue()) {
                return Result.error(CodeMsg.USER_HAD_JOINED_CANT_REJECT);
            }
            userProjectGroup.setStatus(JoinStatus.UN_PASS.getValue());
            if (!userProjectService.update(userProjectGroup)) {
                return Result.error(CodeMsg.UPDATE_ERROR);
            }
        }
        return Result.success();
    }

    @Override
    public List<SelectProjectVO> selectByProjectName(String name) {
        List<SelectProjectVO> list = (List<SelectProjectVO>) redisService.getList(ProjectGroupKey.getByFuzzyName, name);
        if (list == null || list.size() == 0) {
            list = projectGroupMapper.selectByFuzzyName(name);
            if (list != null) {
                redisService.setList(ProjectGroupKey.getByFuzzyName, name, list);
            }
        }
        return list;
    }

    /**
     * ???????????????
     * @param formList
     * @return
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Result changeKeyProjectToGeneral(List<ChangeKeyProjectToGeneralForm> formList) {

        User user = getUserService.getCurrentUser();

        //??????????????????
        List<OperationRecord> list = new ArrayList<>();
        int statu = 0;
        //???????????????id
        List<Long> projectGroupIdList = new LinkedList<>();
        for (ChangeKeyProjectToGeneralForm form : formList
        ) {
            Integer status = keyProjectStatusMapper.getStatusByProjectId(form.getProjectId());
            //??????????????????
            if (!ProjectStatus.GUIDE_TEACHER_ALLOWED.getValue().equals(status) && !ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED.getValue().equals(status)&& !ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue().equals(status)) {
                throw new GlobalException(CodeMsg.CURRENT_PROJECT_STATUS_ERROR);
            }
            statu = status;
            //??????????????????
            OperationRecord operationRecord = new OperationRecord();
            operationRecord.setRelatedId(form.getProjectId());
            operationRecord.setOperationReason(form.getReason());
            if(ProjectStatus.GUIDE_TEACHER_ALLOWED.getValue().equals(status)){
                operationRecord.setOperationUnit(OperationUnit.LAB_ADMINISTRATOR.getValue());
                operationRecord.setOperationType(OperationType.TURN_GENERAL.getValue());
            }else if(ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue().equals(status)){
                operationRecord.setOperationUnit(OperationUnit.SECONDARY_UNIT.getValue());
                operationRecord.setOperationType(OperationType.TURN_GENERAL.getValue());
            }else if(ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue().equals(status)){
                operationRecord.setOperationUnit(OperationUnit.SECONDARY_UNIT.getValue());
                operationRecord.setOperationType(OperationType.TURN_GENERAL.getValue());
            } else{
                operationRecord.setOperationUnit(OperationUnit.FUNCTIONAL_DEPARTMENT.getValue());
                operationRecord.setOperationType(OperationType.FUNCTIONAL_CHANGE_TO_GENERAL.getValue());
            }
            operationRecord.setOperationCollege(user.getInstitute());
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));

            //????????????????????????
            int result = keyProjectStatusMapper.deleteByProjectId(form.getProjectId());
            if (result != 1) {
                throw new GlobalException(CodeMsg.CURRENT_MODIFY_PROJECT_TYPE_ERROR);
            }

            //????????????
//            updateProjectStatus(form.getProjectId(), ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue());

            ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(form.getProjectId());
            //?????????????????????
            projectGroupIdList.add(form.getProjectId());
            projectGroupMapper.updateProjectType(form.getProjectId(), ProjectType.GENERAL.getValue(),form.getApplyFunds());

            //????????????????????????
            String maxTempSerialNumber = projectGroupMapper.getMaxTempSerialNumberByCollege(user.getInstitute(),1);



            log.info(maxTempSerialNumber);
            //??????????????????????????????????????????
            projectGroupMapper.updateProjectTempSerialNumber(form.getProjectId(), SerialNumberUtil.getSerialNumberOfProject(user.getInstitute(), ProjectType.GENERAL.getValue(), maxTempSerialNumber));
            list.add(operationRecord);
        }
        ProjectReview projectReview = projectReviewMapper.selectByCollegeAndType(user.getInstitute(),ProjectType.GENERAL.getValue());

        if(ProjectStatus.GUIDE_TEACHER_ALLOWED.getValue().equals(statu)){
            if(projectReview != null){
                //????????????
                projectGroupMapper.updateProjectStatusOfList(projectGroupIdList, ProjectStatus.PROJECT_REVIEW.getValue());
            }else{
                projectGroupMapper.updateProjectStatusOfList(projectGroupIdList, ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue());
            }
        }else if(ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue().equals(statu)){
            projectGroupMapper.updateProjectStatusOfList(projectGroupIdList, ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue());
        }else if(ProjectStatus.SECONDARY_UNIT_ALLOWED.getValue().equals(statu)){
            projectGroupMapper.updateProjectStatusOfList(projectGroupIdList, ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED.getValue());
        }else {
            projectGroupMapper.updateProjectStatusOfList(projectGroupIdList, ProjectStatus.ESTABLISH.getValue());
        }
        recordMapper.multiInsert(list);
        return Result.success();
    }

    /**
     * ????????????
     *
     * @param list
     * @return
     */
    @Override
    public Result midTermKeyProjectHitBack(List<ProjectCheckForm> list) {
        return ProjectHitBack(list, OperationUnit.FUNCTIONAL_DEPARTMENT, OperationType.INTERIM_RETURN);
    }

    /**
     * ????????????
     * @param list
     * @return
     */
    @Override
    public Result establishProjectHitBack(List<ProjectCheckForm> list) {
        return ProjectHitBack(list, OperationUnit.FUNCTIONAL_DEPARTMENT, OperationType.FUNCTIONAL_ESTABLISH_RETURN);
    }

    /**
     * ???????????????????????????
     *
     * @param projectGradeList
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result CollegeGivesRating(List<ProjectGrade> projectGradeList) {
        User user = getUserService.getCurrentUser();
        //????????????
        if (!userRoleService.validContainsUserRole(RoleType.COLLEGE_FINALIZATION_REVIEW)) {
            throw new GlobalException(CodeMsg.PERMISSION_DENNY);
        }
        List<ProjectCheckForm> list = new LinkedList<>();
        for (ProjectGrade projectGrade : projectGradeList) {
            ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectGrade.getProjectId());
            if (!projectGroup.getStatus().equals(ProjectStatus.ESTABLISH.getValue())) {
                throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
            }
            ProjectCheckForm projectCheckForm = new ProjectCheckForm();
            BeanUtils.copyProperties(projectGrade, projectCheckForm);
            projectCheckForm.setReason("????????????????????????,?????????"+GeneralGrade.getTips(projectGrade.getValue()));
            list.add(projectCheckForm);
        }
        //????????????
        setProjectGrade(projectGradeList, user, 1);
        return setProjectStatusAndRecord(list, OperationType.COLLEGE_PASSED_THE_EXAMINATION, OperationUnit.COLLEGE_REVIEWER, ProjectStatus.COLLEGE_FINAL_SUBMISSION);

    }

    @Override
    public Result CollegeHitBack(List<ProjectCheckForm> list) {
        return ProjectHitBack(list, OperationUnit.COLLEGE_REVIEWER, OperationType.COLLEGE_RETURNS);
    }

    /**
     * ?????????????????????????????????
     *
     * @param list
     * @return
     */
    @Override
    public Result functionalGivesRating(List<ProjectCheckForm> list) {
        User user = getUserService.getCurrentUser();
        //????????????
        if (!userRoleService.validContainsUserRole(RoleType.FUNCTIONAL_DEPARTMENT) &&
                !userRoleService.validContainsUserRole(RoleType.FUNCTIONAL_DEPARTMENT_LEADER)) {
            throw new GlobalException(CodeMsg.PERMISSION_DENNY);
        }
//        List<ProjectCheckForm> list1 = new LinkedList<>();
//        for (ProjectCheckForm projectCheckForm : list) {
//            ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectCheckForm.getProjectId());
//            if (!projectGroup.getStatus().equals(ProjectStatus.COLLEGE_FINAL_SUBMISSION.getValue())) {
//                if(!(projectGroup.getSubordinateCollege()==0)) {
//                    throw new GlobalException(CodeMsg.PROJECT_CURRENT_STATUS_ERROR);
//                }
//            }
//            list1.add(projectCheckForm);
//        }
        //????????????
        return setProjectStatusAndRecord(list, OperationType.FUNCTIONAL_PASSED_THE_EXAMINATION, OperationUnit.FUNCTIONAL_DEPARTMENT, ProjectStatus.CONCLUDED);
    }

    @Override
    public Result functionHitBack(List<ProjectCheckForm> list) {
        return ProjectHitBack(list, OperationUnit.FUNCTIONAL_DEPARTMENT, OperationType.FUNCTIONAL_RETURNS);
    }


    private void setProjectGrade(List<ProjectGrade> projectGradeList, User user, Integer projectType) {
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

    private void functionSetProjectGrade(List<ProjectGrade> projectGradeList, User user, Integer projectType) {
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


    @Transactional(rollbackFor = GlobalException.class)
    public Result ProjectHitBack(List<ProjectCheckForm> formList, OperationUnit operationUnit, OperationType operationType) {
        User user = getUserService.getCurrentUser();
        List<OperationRecord> list = new LinkedList<>();
        for (ProjectCheckForm form : formList) {
            ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(form.getProjectId());
            Integer status = projectGroup.getStatus();
            if (!status.equals(ProjectStatus.ESTABLISH.getValue()) && !status.equals(ProjectStatus.COLLEGE_FINAL_SUBMISSION.getValue())&& !status.equals(ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED.getValue())) {
                throw new GlobalException(CodeMsg.CURRENT_PROJECT_STATUS_ERROR);
            }
            //??????????????????
            OperationRecord operationRecord = new OperationRecord();

            operationRecord.setRelatedId(form.getProjectId());
            operationRecord.setOperationReason("????????????");
            operationRecord.setOperationUnit(operationUnit.getValue());
            operationRecord.setOperationType(operationType.getValue());
            operationRecord.setOperationCollege(user.getInstitute());
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));
            if (operationType.getValue().equals(OperationType.INTERIM_RETURN.getValue())) {
                updateProjectStatus(form.getProjectId(), ProjectStatus.INTERIM_RETURN_MODIFICATION.getValue());
            } else if (operationType.getValue().equals(OperationType.COLLEGE_RETURNS.getValue())) {
                updateProjectStatus(form.getProjectId(), ProjectStatus.COLLEGE_RETURNS.getValue());
            } else if (operationType.getValue().equals(OperationType.FUNCTIONAL_RETURNS.getValue())) {
                updateProjectStatus(form.getProjectId(), ProjectStatus.FUNCTIONAL_RETURNS.getValue());
            } else if (operationType.getValue().equals(OperationType.FUNCTIONAL_ESTABLISH_RETURN.getValue())){
                updateProjectStatus(form.getProjectId(), ProjectStatus.FUNCTIONAL_ESTABLISH_RETURNS.getValue());
            }else {
                throw new GlobalException(CodeMsg.CURRENT_PROJECT_STATUS_ERROR);
            }
            list.add(operationRecord);

            //????????????
            HitBackMessage hitBackMessage = new HitBackMessage();
            UserProjectGroup leader = userProjectGroupMapper.getProjectLeader(form.getProjectId(), MemberRole.PROJECT_GROUP_LEADER.getValue());
            if(leader == null){
                hitBackMessage.setReceiveUserId(userProjectGroupMapper.getProjectLeader(form.getProjectId(), MemberRole.GUIDANCE_TEACHER.getValue()).getUserId());
            }else {
                hitBackMessage.setReceiveUserId(userProjectGroupMapper.getProjectLeader(form.getProjectId(), MemberRole.PROJECT_GROUP_LEADER.getValue()).getUserId());
            }
            hitBackMessage.setContent("?????????:" + projectGroup.getProjectName() + "  ??????:" + form.getReason());
            hitBackMessage.setSender(user.getRealName());
            Date date = new Date();
            hitBackMessage.setSendTime(date);
            hitBackMessage.setIsRead(false);
            log.info(hitBackMessage.toString());
            hitBackMessageMapper.insert(hitBackMessage);

        }
        recordMapper.multiInsert(list);

        return Result.success();
    }

    @Override
    public Result rejectProjectApplyByLabAdministrator(List<ProjectCheckForm> formList) {
        return rejectProjectApply(formList, OperationUnit.LAB_ADMINISTRATOR, OperationType.REJECT);
    }

    @Override
    public Result rejectProjectReportByLabAdministrator(List<ProjectCheckForm> formList) {
        return rejectProjectApply(formList, OperationUnit.LAB_ADMINISTRATOR, OperationType.REPORT_REJECT);
    }

    @Override
    public Result rejectProjectApplyBySecondaryUnit(List<ProjectCheckForm> formList) {
        return rejectProjectApply(formList, OperationUnit.SECONDARY_UNIT, OperationType.REJECT);
    }

    @Override
    public Result rejectProjectApplyByFunctionalDepartment(List<ProjectCheckForm> formList) {
        return rejectProjectApply(formList, OperationUnit.FUNCTIONAL_DEPARTMENT, OperationType.REJECT);
    }

    @Override
    public Result rejectIntermediateInspectionProject(List<ProjectCheckForm> list) {
        return rejectProjectApply(list, OperationUnit.FUNCTIONAL_DEPARTMENT, OperationType.OFFLINE_CHECK_REJECT);
    }

    @Override
    public Result CollegeRejectToBeConcludingProject(List<ProjectCheckForm> list) {
        return rejectProjectApply(list, OperationUnit.COLLEGE_REVIEWER, OperationType.CONCLUSION_REJECT);
    }


    @Override
    public Result rejectToBeConcludingProject(List<ProjectCheckForm> list) {
        return rejectProjectApply(list, OperationUnit.FUNCTIONAL_DEPARTMENT, OperationType.CONCLUSION_REJECT);
    }


    /**
     * ?????????????????????  ???????????????????????????????????????
     *
     * @param formList ????????????????????????
     * @return
     */
    @Transactional(rollbackFor = GlobalException.class)
    Result rejectProjectApply(List<ProjectCheckForm> formList, OperationUnit operationUnit, OperationType operationType) {
        User user = getUserService.getCurrentUser();
        List<OperationRecord> list = new LinkedList<>();
        Integer rightProjectStatus;
        switch (operationUnit.getValue()) {
            //???????????????????????????
            case 5:
                rightProjectStatus = ProjectStatus.LAB_ALLOWED_AND_REPORTED.getValue();
                break;
            case 6:
                rightProjectStatus = ProjectStatus.SECONDARY_UNIT_ALLOWED_AND_REPORTED.getValue();
                break;
            default:
                rightProjectStatus = ProjectStatus.ESTABLISH_FAILED.getValue();
                break;
        }

        for (ProjectCheckForm form : formList
        ) {
            Integer status = projectGroupMapper.selectByPrimaryKey(form.getProjectId()).getStatus();
            //??????????????????
            /**
             * ????????? ????????????bug
             */
            if (!rightProjectStatus.equals(status) && operationUnit != OperationUnit.LAB_ADMINISTRATOR && operationUnit != OperationUnit.FUNCTIONAL_DEPARTMENT
                    && operationUnit != OperationUnit.COLLEGE_REVIEWER) {
                throw new GlobalException(CodeMsg.CURRENT_PROJECT_STATUS_ERROR);
            }
            //??????????????????
            OperationRecord operationRecord = new OperationRecord();

            operationRecord.setRelatedId(form.getProjectId());
            operationRecord.setOperationReason(form.getReason());
            operationRecord.setOperationUnit(operationUnit.getValue());
            operationRecord.setOperationType(operationType.getValue());
            operationRecord.setOperationCollege(user.getInstitute());
            operationRecord.setOperationExecutorId(Long.valueOf(user.getCode()));
            //????????????
            if (operationUnit == OperationUnit.LAB_ADMINISTRATOR ||
                    operationUnit == OperationUnit.MENTOR) {
                updateProjectStatus(form.getProjectId(), ProjectStatus.REJECT_MODIFY.getValue());
            } else {
                updateProjectStatus(form.getProjectId(), ProjectStatus.ESTABLISH_FAILED.getValue());
            }
            list.add(operationRecord);
        }
        recordMapper.multiInsert(list);
        return Result.success();
    }

    private void setOperationExecutor(OperationRecord operationRecord) {
        User user = getUserService.getCurrentUser();
        Long id = Long.valueOf(user.getCode());
        operationRecord.setOperationExecutorId(id);
    }


    @Value(value = "${file.ip-address}")
    private String ipAddress;

    /**
     * ??????????????????
     *
     * @param projectId
     * @return
     */
    @Override
    public Result getProjectGroupDetailVOByProjectId(Long projectId) {
        if (projectId == null) {
            throw new GlobalException(CodeMsg.PARAM_CANT_BE_NULL);
        }
        ProjectGroupDetailVO detail = projectGroupMapper.getProjectGroupDetailVOByProjectId(projectId);
        //????????????
        if (detail.getKeyProjectStatus() != null) {
            detail.setStatus(detail.getKeyProjectStatus());
        }
        ProjectFile file = projectFileMapper.selectByProjectGroupIdAndMaterialType(projectId, MaterialType.APPLY_MATERIAL.getValue(), null);
        if (file == null) {
            detail.setApplyurl(null);
        } else {
            String fileName = file.getFileName();
            String url = ipAddress + "/apply/" + fileName;
            detail.setApplyurl(url);
        }
        return Result.success(detail);
    }

    /**
     * ????????????????????????????????????
     *
     * @param projectId
     * @return
     */
    @Override
    public Result getProjectGroupConclusionDetailByProjectId(Long projectId) {
        if (projectId == null) {
            throw new GlobalException(CodeMsg.PARAM_CANT_BE_NULL);
        }
        ProjectGroupConclusionDetailVO conclusionDetailVO = new ProjectGroupConclusionDetailVO();
        ProjectGroupDetailVO detail = projectGroupMapper.getProjectGroupDetailVOByProjectId(projectId);
        //????????????
        if (detail.getKeyProjectStatus() != null) {
            detail.setStatus(detail.getKeyProjectStatus());
        }
        BeanUtils.copyProperties(detail, conclusionDetailVO);
        ProjectFile file = projectFileMapper.selectByProjectGroupIdAndMaterialType(projectId, MaterialType.APPLY_MATERIAL.getValue(), null);
        log.info(String.valueOf(file));
        ProjectFile conclusionPdf = projectFileMapper.selectByProjectGroupIdAndMaterialType(projectId, MaterialType.CONCLUSION_MATERIAL.getValue(), uploadConfig.getConcludingFileName());
        ProjectFile experimentReportPdf = projectFileMapper.selectByProjectGroupIdAndMaterialType(projectId, MaterialType.EXPERIMENTAL_REPORT.getValue(), uploadConfig.getExperimentReportFileName());
        if (file == null) {
            detail.setApplyurl(null);
        } else {
            String fileName = file.getFileName();
            String url = ipAddress + "/apply/" + fileName;
            conclusionDetailVO.setApplyUrl(url);
        }
        if (conclusionPdf == null) {
            conclusionDetailVO.setConclusionPdf(null);
        } else {
            ProjectAnnex conclusionFile = new ProjectAnnex();
            String url = ipAddress + "/conclusion/" + conclusionPdf.getFileName();
            BeanUtils.copyProperties(conclusionPdf, conclusionFile);
            conclusionFile.setUrl(url);
            conclusionDetailVO.setConclusionPdf(conclusionFile);
        }
        if (experimentReportPdf == null) {
            conclusionDetailVO.setExperimentReportPdf(null);
        } else {
            ProjectAnnex experimentReport = new ProjectAnnex();
            String url = ipAddress + "/conclusion/" + experimentReportPdf.getFileName();
            BeanUtils.copyProperties(experimentReportPdf, experimentReport);
            experimentReport.setUrl(url);
            conclusionDetailVO.setExperimentReportPdf(experimentReport);
        }
        //???????????????????????????
        List<ProjectAnnex> projectAnnexes = projectFileMapper.selectAnnexByProjectGroupId(projectId);
        for (ProjectAnnex projectAnnex : projectAnnexes) {
            String url = ipAddress + "/conclusionAnnex/" + projectAnnex.getFileName();
            projectAnnex.setUrl(url);
        }
        conclusionDetailVO.setAnnexes(projectAnnexes);
        //??????????????????
        if (detail.getKeyProjectStatus() != null) {
            //????????????????????????????????????
            List<Achievement> achievements = achievementMapper.selectByProjectId(projectId);
            List<ProjectOutcomeVO> outcomeVOS = new LinkedList<>();
            for (Achievement achievement : achievements) {
                ProjectOutcomeVO projectOutcomeVO = new ProjectOutcomeVO();
                BeanUtils.copyProperties(achievement, projectOutcomeVO);
                outcomeVOS.add(projectOutcomeVO);
            }
            conclusionDetailVO.setListProjectOutcomeVO(outcomeVOS);
            ProjectFile achievementAnnex = projectFileMapper.selectByProjectGroupIdAndMaterialType(projectId, MaterialType.ACHIEVEMENT_ANNEX.getValue(), null);
            if (achievementAnnex == null) {
                conclusionDetailVO.setAchievementAnnex(null);
            } else {
                ProjectAnnex annex = new ProjectAnnex();
                BeanUtils.copyProperties(achievementAnnex, annex);
                conclusionDetailVO.setAchievementAnnex(annex);
            }
        }
        return Result.success(conclusionDetailVO);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Result deleteMemberFromProject(Long projectId, Long userId) {
        User currentUser = getUserService.getCurrentUser();
        Long currentUserId = Long.valueOf(currentUser.getCode());
        if (userProjectGroupMapper.selectByProjectGroupIdAndUserId(projectId, currentUserId) == null
                || !userProjectGroupMapper.selectByProjectGroupIdAndUserId(projectId, currentUserId).getMemberRole()
                .equals(MemberRole.GUIDANCE_TEACHER.getValue())) {
            int SubordinateCollege = projectGroupMapper.selectSubordinateCollege(projectId);
            if (SubordinateCollege != 39) {
                throw new GlobalException(CodeMsg.PERMISSION_DENNY);
            }
        }
        UserProjectGroup userProjectGroup = userProjectGroupMapper.selectByProjectGroupIdAndUserId(projectId, userId);
        if (userProjectGroup == null) {
            throw new GlobalException(CodeMsg.USER_GROUP_NOT_EXIST);
        }
        //??????????????????
        UserProjectAccount userProjectAccount2 = userProjectAccountMapper.selectByCode(String.valueOf(userId));
        //?????????????????????
        if(userProjectAccount2 != null) {
            ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectId);
            if (projectGroup.getProjectType().equals(ProjectType.GENERAL.getValue())) {
                userProjectAccount2.setGeneralNum(userProjectAccount2.getGeneralNum() - 1);
            } else {
                userProjectAccount2.setKeyNum(userProjectAccount2.getKeyNum() - 1);
            }
            userProjectAccountMapper.updateByPrimaryKey(userProjectAccount2);
        }
        userProjectGroupMapper.deleteByPrimaryKey(userProjectGroup.getId());
        return Result.success();
    }
}
