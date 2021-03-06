package com.swpu.uchain.openexperiment.service.impl;

import cn.hutool.core.util.ZipUtil;
import com.swpu.uchain.openexperiment.DTO.AttachmentFileDTO;
import com.swpu.uchain.openexperiment.DTO.ConclusionDTO;
import com.swpu.uchain.openexperiment.VO.announcement.NewsImagesVO;
import com.swpu.uchain.openexperiment.VO.file.AttachmentFileVO;
import com.swpu.uchain.openexperiment.VO.project.ProjectAnnex;
import com.swpu.uchain.openexperiment.VO.project.ProjectTableInfo;
import com.swpu.uchain.openexperiment.VO.project.UploadAttachmentFileVO;
import com.swpu.uchain.openexperiment.VO.user.UserMemberVO;
import com.swpu.uchain.openexperiment.config.UploadConfig;
import com.swpu.uchain.openexperiment.domain.*;
import com.swpu.uchain.openexperiment.enums.*;
import com.swpu.uchain.openexperiment.form.project.GenericId;
import com.swpu.uchain.openexperiment.mapper.*;
import com.swpu.uchain.openexperiment.exception.GlobalException;
import com.swpu.uchain.openexperiment.redis.RedisService;
import com.swpu.uchain.openexperiment.redis.key.FileKey;
import com.swpu.uchain.openexperiment.result.Result;
import com.swpu.uchain.openexperiment.service.GetUserService;
import com.swpu.uchain.openexperiment.service.ProjectFileService;
import com.swpu.uchain.openexperiment.service.TimeLimitService;
import com.swpu.uchain.openexperiment.service.UserProjectService;
import com.swpu.uchain.openexperiment.util.*;
import io.lettuce.core.GeoArgs;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @Description
 * @Author cby
 * @Date 19-1-22
 **/
@Service
@Slf4j
public class ProjectFileServiceImpl implements ProjectFileService {
    @Autowired
    private UploadConfig uploadConfig;
    @Autowired
    private ProjectFileMapper projectFileMapper;
    @Autowired
    private GetUserService getUserService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ConvertUtil convertUtil;
    @Autowired
    private ProjectGroupMapper projectGroupMapper;
    @Autowired
    private KeyProjectStatusMapper keyProjectStatusMapper;

    @Autowired
    private UserProjectGroupMapper userProjectGroupMapper;
    @Autowired
    private UserProjectService userProjectService;
    @Autowired
    private TimeLimitService timeLimitService;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private UserMapper userMapper;
    @Value(value = "${file.ip-address}")
    private String ipAddress;

    /**
     * ?????????????????????
     *
     * @param roleType ???????????????
     * @return
     */
    private boolean validContainsUserRole(RoleType roleType) {
        User user = getUserService.getCurrentUser();
        //???????????????
        List<UserRole> list = userRoleMapper.selectByUserId(Long.valueOf(user.getCode()));
        if (list == null || list.size() == 0) {
            throw new GlobalException(CodeMsg.PERMISSION_DENNY);
        }

        for (UserRole userRole : list
        ) {
            if (roleType.getValue().equals(userRole.getRoleId())) {
                return true;
            }
        }
        return false;
    }

    public boolean insert(ProjectFile projectFile) {

        //????????????????????????????????? ??????????????????????????????
        if (projectFile.getMaterialType() == 1 || projectFile.getMaterialType() == 11) {
            ProjectFile projectFile1 = projectFileMapper.selectByProjectGroupIdAndMaterialType(projectFile.getProjectGroupId(), projectFile.getMaterialType(), null);
            if (projectFile1 != null) {
                projectFile.setId(projectFile1.getId());
                return update(projectFile);
            }
        } else {
            ProjectFile projectFile1 = projectFileMapper.selectByProjectGroupIdAndFileName(projectFile.getProjectGroupId(), projectFile.getFileName());
            if (projectFile1 != null) {
                projectFile.setId(projectFile1.getId());
                return update(projectFile);
            }
        }
        return projectFileMapper.insert(projectFile) == 1;
    }

    public boolean update(ProjectFile projectFile) {
        return projectFileMapper.updateByPrimaryKey(projectFile) == 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        //TODO ?????????
        ProjectFile projectFile = selectById(id);
        if (projectFile == null) {
            throw new GlobalException(CodeMsg.FILE_NOT_EXIST);
        }
        redisService.delete(FileKey.getById, id + "");
        //??????????????????
        /**
         * 1 ????????????
         * 2 ????????????
         * 3 ????????????
         * 10 ??????
         * 11 ????????????
         */
        Integer materialType = projectFile.getMaterialType();
        //???????????????????????????

        //?????????????????????????????????
        if(materialType == 2 || materialType == 3){
            String docPath = projectFile.getFileName().replace("pdf","doc");
            log.info(docPath);
             FileUtil.deleteFile(FileUtil.getFileRealPath(
                    uploadConfig.getConclusionDir(),
                    docPath));
            if (FileUtil.deleteFile(FileUtil.getFileRealPath(
                    uploadConfig.getConclusionPdf(),
                    projectFile.getFileName()))) {
                projectFileMapper.deleteByPrimaryKey(id);
            }
        }
        //??????
        else if(materialType == 10 ){
            if (FileUtil.deleteFile(FileUtil.getFileRealPath(
                    uploadConfig.getConclusionAnnex(),
                    projectFile.getFileName()))) {
                projectFileMapper.deleteByPrimaryKey(id);
            }
        }
        //????????????
        else if(materialType == 11){
            if (FileUtil.deleteFile(FileUtil.getFileRealPath(
                    uploadConfig.getAchievementAnnex(),
                    projectFile.getFileName()))) {
                projectFileMapper.deleteByPrimaryKey(id);
            }
        }
        else {
            throw new GlobalException(CodeMsg.DELETE_FILE_ERROR);
        }
    }

    @Override
    public ProjectFile selectById(Long id) {
        return projectFileMapper.selectByPrimaryKey(id);
    }

    @Override
    public ProjectFile getAimNameProjectFile(Long projectGroupId, String aimFileName) {
        return projectFileMapper.selectByGroupIdFileName(projectGroupId, aimFileName);
    }

    /**
     * ????????????????????????
     * @param file
     * @param headFile
     * @param projectGroupId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Result uploadApplyDoc(MultipartFile file, MultipartFile headFile, Long projectGroupId) {
        //???????????????????????????
        if (file == null || headFile == null) {
            throw new GlobalException(CodeMsg.UPLOAD_CANT_BE_EMPTY);
        }

        User user = getUserService.getCurrentUser();

        //??????????????????????????????????????????
        UserProjectGroup userProjectGroup = userProjectGroupMapper.selectByProjectGroupIdAndUserId(projectGroupId, Long.valueOf(user.getCode()));
        if (userProjectGroup == null || !userProjectGroup.getMemberRole().equals(MemberRole.PROJECT_GROUP_LEADER.getValue())) {
            int SubordinateCollege = projectGroupMapper.selectSubordinateCollege(projectGroupId);
            if (SubordinateCollege != 39) {
                throw new GlobalException(CodeMsg.UPLOAD_PERMISSION_DENNY);
            }
        }
        ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectGroupId);

        //??????????????????????????????????????????????????????
        if (validContainsUserRole(RoleType.FUNCTIONAL_DEPARTMENT)) {
            log.info("????????????????????????????????????" + projectGroupId + "???????????????????????????");
            //?????????????????????????????????????????????????????????????????????
        } else if (!projectGroup.getStatus().equals(ProjectStatus.LAB_ALLOWED.getValue()) &&
                !projectGroup.getStatus().equals(ProjectStatus.REJECT_MODIFY.getValue())) {
            if(keyProjectStatusMapper.getStatusByProjectId(projectGroupId) !=null){
                //???????????????????????????????????????,??????????????????????????????
                if(!keyProjectStatusMapper.getStatusByProjectId(projectGroupId).equals(ProjectStatus.TO_DE_CONFIRMED.getValue())
                        && !keyProjectStatusMapper.getStatusByProjectId(projectGroupId).equals(ProjectStatus.INTERIM_RETURN_MODIFICATION.getValue())
                        && !keyProjectStatusMapper.getStatusByProjectId(projectGroupId).equals(ProjectStatus.FUNCTIONAL_ESTABLISH_RETURNS.getValue())){
                    throw new GlobalException(CodeMsg.UPLOAD_PROJECT_CURRENT_STATUS_ERROR);
                }
            }else{
                throw new GlobalException(CodeMsg.UPLOAD_PROJECT_CURRENT_STATUS_ERROR);
            }
        }

        if (!getFileSuffix(file.getOriginalFilename()).equals(".doc") || !getFileSuffix(headFile.getOriginalFilename()).equals(".html")) {
            throw new GlobalException(CodeMsg.FORMAT_UNSUPPORTED);
        }
        //????????????????????????
        String bodyDocPath = FileUtil.getFileRealPath(projectGroupId,
                uploadConfig.getApplyDir(),
                uploadConfig.getApplyFileName() + getFileSuffix(file.getOriginalFilename()));
        //??????????????????html
        String headHtmlPath = FileUtil.getFileRealPath(projectGroupId,
                uploadConfig.getApplyDir2(),
                uploadConfig.getApplyFileName() + getFileSuffix(headFile.getOriginalFilename()));

        //??????????????????doc??????
        //?????????????????????
        File dest = new File(bodyDocPath);
        dest.delete();


        if (!checkFileFormat(file, FileType.WORD.getValue())) {
            return Result.error(CodeMsg.FORMAT_UNSUPPORTED);
        }


        //TODO ???????????????????????????
//        ProjectFile projectFile1 = projectFileMapper.selectByProjectGroupIdAndMaterialType(projectGroupId,MaterialType.APPLY_MATERIAL.getValue(), null);

        ProjectFile projectFile = new ProjectFile();
        projectFile.setUploadUserId(Long.valueOf(user.getCode()));
        projectFile.setFileType(FileType.WORD.getValue());
        String fileName = projectGroupId + "_" + uploadConfig.getApplyFileName() + ".pdf";
        projectFile.setFileName(fileName);
        projectFile.setSize(FileUtil.FormatFileSize(file.getSize()));
        projectFile.setUploadTime(new Date());
        projectFile.setDownloadTimes(0);
        projectFile.setMaterialType(MaterialType.APPLY_MATERIAL.getValue());
        projectFile.setProjectGroupId(projectGroupId);
        if (!insert(projectFile)) {
            return Result.error(CodeMsg.ADD_ERROR);
        }
        //????????????????????????PDF
        if (FileUtil.uploadFile(file, bodyDocPath) && FileUtil.uploadFile(headFile, headHtmlPath)) {

            //?????????HTML?????????PDF
//            try {
//                DocumentTransformUtil.html2doc(new File(headDocPath), FileUtils.readFileToString(new File(headHtmlPath),"UTF-8"));
//            } catch (IOException e) {
//                throw new GlobalException(CodeMsg.FILE_NOT_EXIST);
//            }
            //?????????PDF
            String pdfHeadPath = FileUtil.getFileRealPath(projectGroupId,
                    uploadConfig.getPdfTempDir(),
                    uploadConfig.getApplyFileName() + "head" + ".pdf");
            String pdfBodyPath = FileUtil.getFileRealPath(projectGroupId,
                    uploadConfig.getPdfTempDir(),
                    uploadConfig.getApplyFileName() + "body" + ".pdf");


            //?????????PDF
            //??????PDF?????????????????????PDF?????????????????????????????????PDF??????
            String pdfPath = FileUtil.getFileRealPath(projectGroupId,
                    uploadConfig.getApplyDir(),
                    uploadConfig.getApplyFileName() + ".pdf");

            //?????????PDF
            try {
                Html2PDFUtil.convertHtml2PDF(headHtmlPath, pdfHeadPath);
                log.info("????????????HTML????????????PDF----------------");
                PDFConvertUtil.Word2Pdf(bodyDocPath, pdfBodyPath);
                log.info("???????????????????????????PDF----------------");
            } catch (IOException e) {
                throw new GlobalException(CodeMsg.PDF_CONVERT_ERROR);
            }

            log.info("????????????PDF-------");
            mergePdf(pdfHeadPath, pdfBodyPath, pdfPath);

            Result result = deleteTempFile(projectGroupId);
            log.info(result.toString());
            Map<String, String> map = new HashMap<>();
            map.put("url", ipAddress + "/apply/" + fileName);
            return Result.success(map);
        }
        return Result.error(CodeMsg.UPLOAD_ERROR);
    }

    @Override
    public void batchDownload(List<GenericId> genericIds,HttpServletResponse response) {
        log.info(genericIds.toString());
        File delete = new File(uploadConfig.getDownloadTemp());
        try {
            FileUtils.forceDelete(delete);
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (GenericId genericId : genericIds) {
            String projectName = projectGroupMapper.selectByPrimaryKey(genericId.getId()).getProjectName();
            String fileName = genericId.getId() + "_" + uploadConfig.getApplyFileName() + ".pdf";
            File source = new File(uploadConfig.getApplyDir()+"/"+fileName);
            File dest = new File(uploadConfig.getDownloadTemp()+"/"+projectName+".pdf");
            try {
                FileUtils.copyFile(source,dest);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ZipUtil.zip(uploadConfig.getDownloadTemp(), uploadConfig.getDownloadZipTemp()+"/BatchDownload.zip");
        if (FileUtil.downloadFile(response, uploadConfig.getDownloadZipTemp()+"/BatchDownload.zip")) {
            throw new GlobalException(CodeMsg.DOWNLOAD_ERROR);
        }
    }

    private Result deleteTempFile(Long projectId){
        String fileName1 = projectId + "_" + uploadConfig.getApplyFileName() + ".html";
        String fileName2 = projectId + "_" + uploadConfig.getApplyFileName()+"head" + ".pdf";
        String fileName3 = projectId + "_" + uploadConfig.getApplyFileName()+"body" + ".pdf";

        FileUtil.deleteFile(FileUtil.getFileRealPath(
                uploadConfig.getApplyDir2(),
                fileName1));
        log.info("??????1");
        FileUtil.deleteFile(FileUtil.getFileRealPath(
                uploadConfig.getPdfTempDir(),
                fileName2));
        log.info("");
        FileUtil.deleteFile(FileUtil.getFileRealPath(
                uploadConfig.getPdfTempDir(),
                fileName3));
        return Result.success();
    }
    @Override
    public void downloadApplyFile(Long fileId, HttpServletResponse response) {
        ProjectFile projectFile = projectFileMapper.selectByPrimaryKey(fileId);
        if (projectFile == null) {
            throw new GlobalException(CodeMsg.FILE_NOT_EXIST);
        }
        String realPath = uploadConfig.getApplyDir() + '/' + projectFile.getFileName();
        if (FileUtil.downloadFile(response, realPath)) {
            throw new GlobalException(CodeMsg.DOWNLOAD_ERROR);
        }
        projectFile.setDownloadTimes(projectFile.getDownloadTimes() + 1);
        if (!update(projectFile)) {
            throw new GlobalException(CodeMsg.UPDATE_ERROR);
        }
    }

    @Override
    public void getConclusionDoc(Long fileId, HttpServletResponse response) {
        ProjectFile projectFile = projectFileMapper.selectByPrimaryKey(fileId);
        if (projectFile == null) {
            throw new GlobalException(CodeMsg.FILE_NOT_EXIST);
        }
        String realPath = uploadConfig.getConclusionDir() + "/" + projectFile.getFileName();
        if (FileUtil.downloadFile(response, realPath)) {
            throw new GlobalException(CodeMsg.DOWNLOAD_ERROR);
        }
        projectFile.setDownloadTimes(projectFile.getDownloadTimes() + 1);
        if (!update(projectFile)) {
            throw new GlobalException(CodeMsg.UPDATE_ERROR);
        }
    }

    @Override
    public void downloadApplyPdf(Long fileId, HttpServletResponse response) {
        ProjectFile projectFile = projectFileMapper.selectByPrimaryKey(fileId);
        if (projectFile == null) {
            throw new GlobalException(CodeMsg.FILE_NOT_EXIST);
        }

        String realPath = FileUtil.getFileRealPath(
                projectFile.getProjectGroupId(),
                uploadConfig.getApplyDir(),
                FileUtil.getFileNameWithoutSuffix(projectFile.getFileName()));
        File file = new File(realPath);
        if (file.exists()) {
            if (FileUtil.downloadFile(response, realPath)) {
                throw new GlobalException(CodeMsg.DOWNLOAD_ERROR);
            }
        }
        if (FileUtil.downloadFile(response, realPath)) {
            throw new GlobalException(CodeMsg.DOWNLOAD_ERROR);
        }
    }

    @Override
    public List<ProjectFile> getProjectAllFiles(Long projectGroupId) {
//        return projectFileMapper.selectByProjectGroupIdAndMaterialType(projectGroupId,null);
        return null;
    }

    @Override
    public Result uploadAttachmentFile(List<MultipartFile> multipartFile, Long projectId) {
        //??????????????????
//        timeLimitService.validTime(TimeLimitType.UPLOADING_INFORMATION);
        ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectId);
        if(projectGroup.getKeyProjectStatus() != null){
            if(projectGroup.getKeyProjectStatus() <= 5){
                throw new GlobalException(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
        }else {
            if(projectGroup.getStatus() <= 5){
                throw new GlobalException(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
        }
        if (multipartFile == null || multipartFile.size() == 0) {
            throw new GlobalException(CodeMsg.UPLOAD_CANT_BE_EMPTY);
        }
        User currentUser = getUserService.getCurrentUser();
        if (userProjectService.selectByProjectGroupIdAndUserId(projectId, Long.valueOf(currentUser.getCode())) == null) {
            return Result.error(CodeMsg.PERMISSION_DENNY);
        }

        List<ProjectAnnex> attachmentUrls = new ArrayList<>();
        for (MultipartFile file : multipartFile) {


            ProjectFile projectFile = new ProjectFile();
            projectFile.setProjectGroupId(projectId);
            projectFile.setFileName(projectId + "_??????_" + file.getOriginalFilename());
            projectFile.setDownloadTimes(0);
            projectFile.setFileType(FileUtil.getType(FileUtil.getMultipartFileSuffix(file)));
            log.info(projectFile.getFileType().toString());
            if (projectFile.getFileType() != 3 && projectFile.getFileType() != 4) {
                throw new GlobalException(CodeMsg.FORMAT_UNSUPPORTED);
            }
            projectFile.setSize(FileUtil.FormatFileSize(file.getSize()));
            projectFile.setUploadTime(new Date());
            projectFile.setMaterialType(MaterialType.ATTACHMENT_FILE.getValue());
            projectFile.setUploadUserId(Long.valueOf(currentUser.getCode()));
            if (!insert(projectFile)) {
                return Result.error(CodeMsg.ADD_ERROR);
            }
            if (!FileUtil.uploadFile(
                    file, uploadConfig.getConclusionAnnex() + "/" + projectFile.getFileName())) {
                return Result.error(CodeMsg.UPLOAD_ERROR);
            }
            ProjectAnnex projectAnnex = new ProjectAnnex();
            BeanUtils.copyProperties(projectFile,projectAnnex);
            projectAnnex.setUrl(ipAddress + "/conclusionAnnex/" + projectFile.getFileName());
            attachmentUrls.add(projectAnnex);
        }
        return Result.success(attachmentUrls);
    }

    /**
     * ?????????????????????
     * @param file
     * @return
     */
    @Override
    public Object uploadImages(MultipartFile file) {
        if (file == null) {
            throw new GlobalException(CodeMsg.UPLOAD_CANT_BE_EMPTY);
        }
        if(FileUtil.getType(FileUtil.getMultipartFileSuffix(file)) != 4){
            return Result.error(CodeMsg.FORMAT_UNSUPPORTED);
        }
        String fileName = System.currentTimeMillis()+getFileSuffix(file.getOriginalFilename());
        String filePath = uploadConfig.getNewsImages() + "/" + fileName;
        if (!FileUtil.uploadFile(
                file, filePath)) {
            return Result.error(CodeMsg.UPLOAD_ERROR);
        }
        NewsImagesVO newsImagesVO = new NewsImagesVO();
        newsImagesVO.setUploaded("1");
        newsImagesVO.setUrl(ipAddress+"/newsImages/"+fileName);

        return newsImagesVO;
    }

    public static void main(String[] args) {
        System.out.println(System.currentTimeMillis()+getFileSuffix("297_??????_pexels-alan-daysh-5198585.jpg"));
    }
    /**
     * ????????????
     *
     * @param fileId
     * @param response
     */
    @Override
    public void downloadAttachmentFile(long fileId, HttpServletResponse response) {
        ProjectFile projectFile = selectById(fileId);
        if (projectFile == null) {
            throw new GlobalException(CodeMsg.FILE_NOT_EXIST);
        }
        if (FileUtil.downloadFile(response, FileUtil.getFileRealPath(uploadConfig.getConclusionAnnex(), projectFile.getFileName()))) {
            throw new GlobalException(CodeMsg.DOWNLOAD_ERROR);
        }
    }

    @Override
    public Result listAttachmentFiles() {
        List<AttachmentFileDTO> attachmentFileDTOS = projectFileMapper.selectAttachmentFiles();
        List<AttachmentFileVO> attachmentFileVOS = convertUtil.getAttachmentFileVOS(attachmentFileDTOS);
        return Result.success(attachmentFileVOS);
    }

    @Override
    public Result uploadConcludingReport(Long projectId, MultipartFile file) {
        ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectId);
        if(projectGroup.getKeyProjectStatus() != null){
            if(projectGroup.getKeyProjectStatus() <= 5){
                throw new GlobalException(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
        }else {
            if(projectGroup.getStatus() <= 5){
                throw new GlobalException(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
        }
        if (file == null) {
            throw new GlobalException(CodeMsg.FILE_EMPTY_ERROR);
        }

        if (projectGroup == null) {
            return Result.error(CodeMsg.PROJECT_GROUP_NOT_EXIST);
        }
        if (!".doc".equals(getFileSuffix(file.getOriginalFilename()))) {
            throw new GlobalException(CodeMsg.FORMAT_UNSUPPORTED);
        }
        User currentUser = getUserService.getCurrentUser();


        if (userProjectService.selectByProjectGroupIdAndUserId(projectId, Long.valueOf(currentUser.getCode())) == null) {
            return Result.error(CodeMsg.PERMISSION_DENNY);
        }

        ProjectFile projectFile;
//                = projectFileMapper.selectByProjectGroupIdAndMaterialType(projectId, MaterialType.CONCLUSION_MATERIAL.getValue(), uploadConfig.getConcludingFileName());
//        //???????????????????????????,????????????????????????
//        if (projectFile != null) {
//            FileUtil.uploadFile(
//                    file,
//                    FileUtil.getFileRealPath(
//                            projectId,
//                            uploadConfig.getConclusionDir(),
//                            uploadConfig.getConcludingFileName() + getFileSuffix(file.getOriginalFilename())));
//        }

        projectFile = new ProjectFile();
        projectFile.setUploadUserId(Long.valueOf(currentUser.getCode()));
        //??????????????????pdf??????
        projectFile.setFileName(projectId + "_" + uploadConfig.getConcludingFileName() + ".pdf");
        projectFile.setUploadTime(new Date());
        projectFile.setMaterialType(MaterialType.CONCLUSION_MATERIAL.getValue());
        projectFile.setSize(FileUtil.FormatFileSize(file.getSize()));
        projectFile.setFileType(FileUtil.getType(FileUtil.getMultipartFileSuffix(file)));
        if (projectFile.getFileType() != 2) {
            throw new GlobalException(CodeMsg.FORMAT_UNSUPPORTED);
        }
        projectFile.setDownloadTimes(0);
        projectFile.setProjectGroupId(projectId);
        String docPath = FileUtil.getFileRealPath(projectId,
                uploadConfig.getConclusionDir(),
                uploadConfig.getConcludingFileName() + getFileSuffix(file.getOriginalFilename()));
        String pdfPath = FileUtil.getFileRealPath(projectId,
                uploadConfig.getConclusionPdf(),
                uploadConfig.getConcludingFileName() + ".pdf");
        if (!FileUtil.uploadFile(
                file,
                docPath)) {
            return Result.error(CodeMsg.UPLOAD_ERROR);
        }
        if (!insert(projectFile)) {
            return Result.error(CodeMsg.ADD_ERROR);
        }
        // ???????????????PDF
        convertDocToPDF(docPath, pdfPath);
        ProjectAnnex projectAnnex = new ProjectAnnex();
        BeanUtils.copyProperties(projectFile,projectAnnex);
        projectAnnex.setUrl(ipAddress + "/conclusion/" + projectFile.getFileName());
        return Result.success(projectAnnex);
    }

    @Override
    public Result uploadExperimentReport(Long projectId, MultipartFile file) {
        //TODO ????????????
        ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectId);
        if(projectGroup.getKeyProjectStatus() != null){
            if(projectGroup.getKeyProjectStatus() <= 5){
                throw new GlobalException(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
        }else {
            if(projectGroup.getStatus() <= 5){
                throw new GlobalException(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
        }
        if (file == null) {
            throw new GlobalException(CodeMsg.FILE_EMPTY_ERROR);
        }

        if (projectGroup == null) {
            return Result.error(CodeMsg.PROJECT_GROUP_NOT_EXIST);
        }

        if (!".doc".equals(getFileSuffix(file.getOriginalFilename()))) {
            throw new GlobalException(CodeMsg.FORMAT_UNSUPPORTED);
        }

        //???????????????????????????,????????????????????????
        ProjectFile projectFile;
//                = projectFileMapper.selectByProjectGroupIdAndMaterialType(projectId, MaterialType.EXPERIMENTAL_REPORT.getValue(), uploadConfig.getConcludingFileName());
//
//        if (projectFile != null) {
//            FileUtil.uploadFile(
//                    file,
//                    FileUtil.getFileRealPath(
//                            projectFile.getId(),
//                            uploadConfig.getConclusionDir(),
//                            uploadConfig.getExperimentReportFileName()));
//        }
        User currentUser = getUserService.getCurrentUser();

        //TODO,??????????????????????????????????????????
        if (userProjectService.selectByProjectGroupIdAndUserId(projectId, Long.valueOf(currentUser.getCode())) == null) {
            return Result.error(CodeMsg.PERMISSION_DENNY);
        }

        projectFile = new ProjectFile();
        projectFile.setUploadUserId(Long.valueOf(currentUser.getCode()));
        //??????????????????pdf??????
        projectFile.setFileName(projectId + "_" + uploadConfig.getExperimentReportFileName() + ".pdf");
        projectFile.setUploadTime(new Date());
        projectFile.setMaterialType(MaterialType.EXPERIMENTAL_REPORT.getValue());
        projectFile.setSize(FileUtil.FormatFileSize(file.getSize()));
        projectFile.setFileType(FileUtil.getType(FileUtil.getMultipartFileSuffix(file)));
        projectFile.setDownloadTimes(0);
        projectFile.setProjectGroupId(projectId);

        if (!insert(projectFile)) {
            return Result.error(CodeMsg.ADD_ERROR);
        }
        String docPath = FileUtil.getFileRealPath(projectId,
                uploadConfig.getConclusionDir(),
                uploadConfig.getExperimentReportFileName() + getFileSuffix(file.getOriginalFilename()));
        String pdfPath = FileUtil.getFileRealPath(projectId,
                uploadConfig.getConclusionPdf(),
                uploadConfig.getExperimentReportFileName() + ".pdf");
        if (!FileUtil.uploadFile(
                file,
                docPath)) {
            return Result.error(CodeMsg.UPLOAD_ERROR);
        }
        // ???????????????PDF
        convertDocToPDF(docPath, pdfPath);
        ProjectAnnex projectAnnex = new ProjectAnnex();
        BeanUtils.copyProperties(projectFile,projectAnnex);
        projectAnnex.setUrl(ipAddress + "/conclusion/" + projectFile.getFileName());
        return Result.success(projectAnnex);
    }

    /**
     * ??????????????????
     * @param projectGroupId
     * @param file
     * @return
     */
    @Override
    public Result uploadAchievementAnnex(Long projectGroupId, MultipartFile file) {
        ProjectGroup projectGroup = projectGroupMapper.selectByPrimaryKey(projectGroupId);
        if(projectGroup.getKeyProjectStatus() != null){
            if(projectGroup.getKeyProjectStatus() <= 5){
                throw new GlobalException(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
        }else {
            if(projectGroup.getStatus() <= 5){
                throw new GlobalException(CodeMsg.PROJECT_GROUP_INFO_CANT_CHANGE);
            }
        }
        if (file == null) {
            throw new GlobalException(CodeMsg.UPLOAD_CANT_BE_EMPTY);
        }
        User currentUser = getUserService.getCurrentUser();
        if (userProjectService.selectByProjectGroupIdAndUserId(projectGroupId, Long.valueOf(currentUser.getCode())) == null) {
            return Result.error(CodeMsg.PERMISSION_DENNY);
        }
        ProjectFile projectFile = new ProjectFile();
        projectFile.setProjectGroupId(projectGroupId);
        projectFile.setFileName(projectGroupId + "_????????????????????????.zip");
        projectFile.setDownloadTimes(0);
        projectFile.setFileType(FileUtil.getType(FileUtil.getMultipartFileSuffix(file)));
        if (projectFile.getFileType() != 5) {
            throw new GlobalException(CodeMsg.FORMAT_UNSUPPORTED);
        }
        projectFile.setSize(FileUtil.FormatFileSize(file.getSize()));
        projectFile.setUploadTime(new Date());
        projectFile.setMaterialType(MaterialType.ACHIEVEMENT_ANNEX.getValue());
        projectFile.setUploadUserId(Long.valueOf(currentUser.getCode()));
        if (!insert(projectFile)) {
            return Result.error(CodeMsg.ADD_ERROR);
        }
        if (!FileUtil.uploadFile(
                file, uploadConfig.getAchievementAnnex() + "/" + projectFile.getFileName())) {
            return Result.error(CodeMsg.UPLOAD_ERROR);
        }
        ProjectAnnex projectAnnex = new ProjectAnnex();
        BeanUtils.copyProperties(projectFile,projectAnnex);
        return Result.success(projectAnnex);
    }

    public boolean checkFileFormat(MultipartFile multipartFile, Integer aimType) {
        String suffix = FileUtil.getMultipartFileSuffix(multipartFile);
        int type = FileUtil.getType(suffix);
        if (type != aimType) {
            return false;
        }
        return true;
    }

    private int getYear() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.YEAR);
    }


    @Override
    public void generateEstablishExcel(HttpServletResponse response, Integer projectStatus) {

        User user = getUserService.getCurrentUser();
        //????????????????????????????????????
        if (user == null) {
            throw new GlobalException(CodeMsg.AUTHENTICATION_ERROR);
        }
        Integer college = user.getInstitute();
        //???????????????????????????????????????
        List<ProjectTableInfo> list = projectGroupMapper.getProjectTableInfoListByCollegeAndList1(college, projectStatus);
        SortListUtil.sort(list,"projectType");
        SortListUtil.sort(list,"college");
        SortListUtil.sort(list,"tempSerialNumber");
        // 1.??????HSSFWorkbook?????????HSSFWorkbook????????????Excel??????
        XSSFWorkbook wb = new XSSFWorkbook();
        // 2.???workbook???????????????sheet,??????Excel????????????sheet(?????????)
        XSSFSheet sheet = wb.createSheet("workSheet");

        sheet.setPrintGridlines(true);
        //3.1??????????????????
        XSSFCellStyle cellStyle = wb.createCellStyle();
        //????????????
        cellStyle.setWrapText(true);
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        //??????????????????
        int index = 0;

        //??????
        XSSFRow title = sheet.createRow(index);
        sheet.setColumnWidth(0, 256 * 150);
        title.setHeight((short) (16 * 50));
        title.createCell(index++).setCellValue("?????????????????????" + getYear() / 100 + "???(" + getYear() + "-" + (getYear() + 1) + "??????)???????????????????????????????????????");

        XSSFRow info = sheet.createRow(index);
        info.createCell(0).setCellValue("?????????????????????");
        sheet.setColumnWidth(0, 256 * 20);
        info.createCell(3).setCellValue("????????????");
        sheet.setColumnWidth(index, 256 * 20);
        index++;

        // 4.????????????????????????????????????
        String[] head = {"???/??????", "????????????", "????????????", "????????????", "????????????", "????????????", "??????"
                , "????????????", "????????????", "????????????", "??????\r\n?????????", "???????????????", "??????????????????", "????????????\r\n??????"
                , "?????????????????????", "??????\r\n????????????", "????????????", "????????????", "????????????"};
        // 4.1???????????????
        XSSFRow row = sheet.createRow(index++);

        //??????????????????
        for (int i = 0; i < head.length; i++) {

            // ??????????????????,??????????????????????????????
            row.setHeight((short) (16 * 40));
            row.createCell(i).setCellValue(head[i]);

        }

        //????????????
        for (ProjectTableInfo projectTableInfo : list) {

            //??????????????????(?????????????????????,??????????????????)
            if (projectTableInfo.getKeyProjectStatus() != null) {
                projectTableInfo.setProjectStatus(projectTableInfo.getKeyProjectStatus());
            }


            //???????????????
            List<UserMemberVO> userMemberVOList =
                    userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.PROJECT_GROUP_LEADER.getValue(), projectTableInfo.getId(), JoinStatus.JOINED.getValue());
            StringBuilder students = new StringBuilder("");
            StringBuilder studentsMajorAndGrade = new StringBuilder();
            StringBuilder leaderName = new StringBuilder();
            StringBuilder leaderPhone = new StringBuilder();
            StringBuilder guideTeachers = new StringBuilder();
            for (int i = 0; i < userMemberVOList.size(); i++) {
                UserMemberVO userMemberVO = userMemberVOList.get(i);
                leaderName.append(userMemberVO.getUserName());
                if (userMemberVO.getPhone() != null) {
                    leaderPhone.append(userMemberVO.getPhone());
                }

                students.append(userMemberVO.getUserName());
                students.append("\r\n ");
                studentsMajorAndGrade.append(ConvertUtil.getGradeAndMajorByNumber(userMemberVO.getGrade() + userMemberVO.getMajor()));
                if (i != userMemberVOList.size() - 1) {
                    studentsMajorAndGrade.append("\r\n ");
                }
            }


            //????????????
            List<UserMemberVO> userMemberVOList2 =
                    userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.NORMAL_MEMBER.getValue(), projectTableInfo.getId(), JoinStatus.JOINED.getValue());
            for (int i = 0; i < userMemberVOList2.size(); i++) {
                UserMemberVO userMemberVO = userMemberVOList2.get(i);
                students.append(userMemberVO.getUserName());
                students.append("\r\n ");
                studentsMajorAndGrade.append(ConvertUtil.getGradeAndMajorByNumber(userMemberVO.getGrade() + userMemberVO.getMajor()));
                if (i != userMemberVOList2.size() - 1) {
                    studentsMajorAndGrade.append("\r\n ");
                }
            }

            //????????????
            List<UserMemberVO> userMemberVOList3 =
                    userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(), projectTableInfo.getId(), JoinStatus.JOINED.getValue());
            for (int i = 0; i < userMemberVOList3.size(); i++) {
                UserMemberVO userMemberVO = userMemberVOList3.get(i);
                guideTeachers.append(userMemberVO.getUserName());
                guideTeachers.append("\r\n ");
                if (i != userMemberVOList3.size() - 1) {
                    studentsMajorAndGrade.append("\r\n ");
                }
            }


            //?????????
            row = sheet.createRow(index++);

            //????????????
            row.setHeight((short) (16 * 22));
            // ??????
            row.createCell(0).setCellValue(ConvertUtil.getStrCollege(projectTableInfo.getCollege()));
            if (projectTableInfo.getTempSerialNumber() != null) {
                row.createCell(1).setCellValue(projectTableInfo.getTempSerialNumber());
            }
            //????????????
            row.createCell(2).setCellValue(projectTableInfo.getProjectName());
            //????????????
            row.createCell(3).setCellValue(ConvertUtil.getStrExperimentType(projectTableInfo.getExperimentType()));

            row.createCell(4).setCellValue(projectTableInfo.getTotalHours());
            row.createCell(5).setCellValue(guideTeachers.toString());
            row.createCell(6).setCellValue(students.toString());
            row.createCell(7).setCellValue(studentsMajorAndGrade.toString());
            log.info(projectTableInfo.getStartTime().substring(0, 10));
            row.createCell(8).setCellValue(projectTableInfo.getStartTime().substring(0, 10));
            row.createCell(9).setCellValue(projectTableInfo.getEndTime().substring(0, 10));
            row.createCell(10).setCellValue(projectTableInfo.getLabName());
            row.createCell(11).setCellValue(projectTableInfo.getAddress());
            row.createCell(12).setCellValue(leaderName.toString());
            row.createCell(13).setCellValue(leaderPhone.length() == 0 ? "" : leaderPhone.toString());
            row.createCell(14).setCellValue(projectTableInfo.getApplyFunds());
            row.createCell(15).setCellValue(ConvertUtil.getStringSuggestGroupType(projectTableInfo.getSuggestGroupType()));
            row.createCell(16).setCellValue(projectTableInfo.getProjectStatus());
            row.createCell(17).setCellValue(projectTableInfo.getTempSerialNumber());
            row.createCell(18).setCellValue(ConvertUtil.getStrProjectType(projectTableInfo.getProjectType()));

        }

        sheet.createRow(index++).createCell(0).setCellValue("???1????????????????????????????????????????????????2????????????????????????A-F,???????????????????????????");
        index++;

        XSSFRow end = sheet.createRow(index);
        end.createCell(0).setCellValue("??????????????????:");
        end.createCell(3).setCellValue("?????????");
        response.setContentType("application/vnd.ms-excel;charset=utf-8");
        response.setHeader("Content-disposition", "attachment;filename=" + "EstablishExcel" + ".xlsx");
        try {
            OutputStream os = response.getOutputStream();
            wb.write(os);
            os.flush();
            os.close();
        } catch (IOException e) {
            throw new GlobalException(CodeMsg.DOWNLOAD_ERROR);
        }
    }

    @Override
    public void generateAllEstablishExcel(HttpServletResponse response, Integer projectStatus) {

        List<ProjectTableInfo> list = projectGroupMapper.getProjectTableInfoListByCollegeAndList(null, projectStatus);



        SortListUtil.sort(list,"projectType");
        SortListUtil.sort(list,"college");
        SortListUtil.sort(list,"tempSerialNumber");
        // 1.??????HSSFWorkbook?????????HSSFWorkbook????????????Excel??????
        XSSFWorkbook wb = new XSSFWorkbook();
        // 2.???workbook???????????????sheet,??????Excel????????????sheet(?????????)
        XSSFSheet sheet = wb.createSheet("workSheet");

        sheet.setPrintGridlines(true);
        //3.1??????????????????
        XSSFCellStyle cellStyle = wb.createCellStyle();
        //????????????
        cellStyle.setWrapText(true);
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        //??????????????????
        int index = 0;

        //??????
        XSSFRow title = sheet.createRow(index);
        sheet.setColumnWidth(0, 256 * 150);
        title.setHeight((short) (16 * 50));
        title.createCell(index++).setCellValue("?????????????????????" + getYear() / 100 + "???(" + getYear() + "-" + (getYear() + 1) + "??????)???????????????????????????????????????");

        XSSFRow info = sheet.createRow(index);
        info.createCell(0).setCellValue("?????????????????????");
        sheet.setColumnWidth(0, 256 * 20);
        info.createCell(3).setCellValue("????????????");
        sheet.setColumnWidth(index, 256 * 20);
        index++;

        // 4.????????????????????????????????????
        String[] head = {"???/??????", "????????????", "????????????", "????????????", "????????????", "????????????", "??????"
                , "????????????", "????????????", "????????????", "??????\r\n?????????", "???????????????", "??????????????????", "????????????\r\n??????"
                , "?????????????????????", "??????\r\n????????????", "????????????", "????????????", "????????????"};
        // 4.1???????????????
        XSSFRow row = sheet.createRow(index++);

        //??????????????????
        for (int i = 0; i < head.length; i++) {

            // ??????????????????,??????????????????????????????
            row.setHeight((short) (16 * 40));
            row.createCell(i).setCellValue(head[i]);

        }

        //????????????
        for (ProjectTableInfo projectTableInfo : list) {

            //??????????????????(?????????????????????,??????????????????)
            if (projectTableInfo.getKeyProjectStatus() != null) {
                projectTableInfo.setProjectStatus(projectTableInfo.getKeyProjectStatus());
            }


            //???????????????
            List<UserMemberVO> userMemberVOList =
                    userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.PROJECT_GROUP_LEADER.getValue(), projectTableInfo.getId(), JoinStatus.JOINED.getValue());
            StringBuilder students = new StringBuilder("");
            StringBuilder studentsMajorAndGrade = new StringBuilder();
            StringBuilder leaderName = new StringBuilder();
            StringBuilder leaderPhone = new StringBuilder();
            StringBuilder guideTeachers = new StringBuilder();
            for (int i = 0; i < userMemberVOList.size(); i++) {
                UserMemberVO userMemberVO = userMemberVOList.get(i);
                leaderName.append(userMemberVO.getUserName());
                if (userMemberVO.getPhone() != null) {
                    leaderPhone.append(userMemberVO.getPhone());
                }

                students.append(userMemberVO.getUserName());
                students.append("\r\n ");
                studentsMajorAndGrade.append(ConvertUtil.getGradeAndMajorByNumber(userMemberVO.getGrade() + userMemberVO.getMajor()));
                if (i != userMemberVOList.size() - 1) {
                    studentsMajorAndGrade.append("\r\n ");
                }
            }


            //????????????
            List<UserMemberVO> userMemberVOList2 =
                    userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.NORMAL_MEMBER.getValue(), projectTableInfo.getId(), JoinStatus.JOINED.getValue());
            for (int i = 0; i < userMemberVOList2.size(); i++) {
                UserMemberVO userMemberVO = userMemberVOList2.get(i);
                students.append(userMemberVO.getUserName());
                students.append("\r\n ");
                studentsMajorAndGrade.append(ConvertUtil.getGradeAndMajorByNumber(userMemberVO.getGrade() + userMemberVO.getMajor()));
                if (i != userMemberVOList2.size() - 1) {
                    studentsMajorAndGrade.append("\r\n ");
                }
            }

            //????????????
            List<UserMemberVO> userMemberVOList3 =
                    userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(), projectTableInfo.getId(), JoinStatus.JOINED.getValue());
            for (int i = 0; i < userMemberVOList3.size(); i++) {
                UserMemberVO userMemberVO = userMemberVOList3.get(i);
                guideTeachers.append(userMemberVO.getUserName());
                guideTeachers.append("\r\n ");
                if (i != userMemberVOList3.size() - 1) {
                    studentsMajorAndGrade.append("\r\n ");
                }
            }


            //?????????
            row = sheet.createRow(index++);

            //????????????
            row.setHeight((short) (16 * 22));
            // ??????
            row.createCell(0).setCellValue(ConvertUtil.getStrCollege(projectTableInfo.getCollege()));
            if (projectTableInfo.getTempSerialNumber() != null) {
                row.createCell(1).setCellValue(projectTableInfo.getTempSerialNumber());
            }
            //????????????
            row.createCell(2).setCellValue(projectTableInfo.getProjectName());
            //????????????
            row.createCell(3).setCellValue(ConvertUtil.getStrExperimentType(projectTableInfo.getExperimentType()));

            row.createCell(4).setCellValue(projectTableInfo.getTotalHours());
            row.createCell(5).setCellValue(guideTeachers.toString());
            row.createCell(6).setCellValue(students.toString());
            row.createCell(7).setCellValue(studentsMajorAndGrade.toString());
            log.info(projectTableInfo.getStartTime().substring(0, 10));
            row.createCell(8).setCellValue(projectTableInfo.getStartTime().substring(0, 10));
            row.createCell(9).setCellValue(projectTableInfo.getEndTime().substring(0, 10));
            row.createCell(10).setCellValue(projectTableInfo.getLabName());
            row.createCell(11).setCellValue(projectTableInfo.getAddress());
            row.createCell(12).setCellValue(leaderName.toString());
            row.createCell(13).setCellValue(leaderPhone.length() == 0 ? "" : leaderPhone.toString());
            row.createCell(14).setCellValue(projectTableInfo.getApplyFunds());
            row.createCell(15).setCellValue(ConvertUtil.getStringSuggestGroupType(projectTableInfo.getSuggestGroupType()));
            row.createCell(16).setCellValue(projectTableInfo.getProjectStatus());
            row.createCell(17).setCellValue(projectTableInfo.getTempSerialNumber());
            row.createCell(18).setCellValue(ConvertUtil.getStrProjectType(projectTableInfo.getProjectType()));

        }

        sheet.createRow(index++).createCell(0).setCellValue("???1????????????????????????????????????????????????2????????????????????????A-F,???????????????????????????");
        index++;

        XSSFRow end = sheet.createRow(index);
        end.createCell(0).setCellValue("??????????????????:");
        end.createCell(3).setCellValue("?????????");
        response.setContentType("application/vnd.ms-excel;charset=utf-8");
        response.setHeader("Content-disposition", "attachment;filename=" + "EstablishExcel" + ".xlsx");
        try {
            OutputStream os = response.getOutputStream();
            wb.write(os);
            os.flush();
            os.close();
        } catch (IOException e) {
            throw new GlobalException(CodeMsg.DOWNLOAD_ERROR);
        }
    }

    @Override
    public void generateWaitEstablishExcel(HttpServletResponse response, Integer projectStatus) {

        List<ProjectTableInfo> list = projectGroupMapper.getProjectTableInfoListByCollegeAndList(null, projectStatus);

        SortListUtil.sort(list,"college");
        // 1.??????HSSFWorkbook?????????HSSFWorkbook????????????Excel??????
        XSSFWorkbook wb = new XSSFWorkbook();
        // 2.???workbook???????????????sheet,??????Excel????????????sheet(?????????)
        XSSFSheet sheet = wb.createSheet("workSheet");

        sheet.setPrintGridlines(true);
        //3.1??????????????????
        XSSFCellStyle cellStyle = wb.createCellStyle();
        //????????????
        cellStyle.setWrapText(true);
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        //??????????????????
        int index = 0;

        //??????
        XSSFRow title = sheet.createRow(index);
        sheet.setColumnWidth(0, 256 * 150);
        title.setHeight((short) (16 * 50));
        title.createCell(index++).setCellValue("?????????????????????" + getYear() / 100 + "???(" + getYear() + "-" + (getYear() + 1) + "??????)???????????????????????????????????????");

        XSSFRow info = sheet.createRow(index);
        info.createCell(0).setCellValue("?????????????????????");
        sheet.setColumnWidth(0, 256 * 20);
        info.createCell(3).setCellValue("????????????");
        sheet.setColumnWidth(index, 256 * 20);
        index++;

        // 4.????????????????????????????????????
        String[] head = {"???/??????", "????????????", "????????????", "????????????", "????????????", "????????????", "??????"
                , "????????????", "????????????", "????????????", "??????\r\n?????????", "???????????????", "??????????????????", "????????????\r\n??????"
                , "?????????????????????", "??????\r\n????????????", "????????????", "????????????", "????????????"};
        // 4.1???????????????
        XSSFRow row = sheet.createRow(index++);

        //??????????????????
        for (int i = 0; i < head.length; i++) {

            // ??????????????????,??????????????????????????????
            row.setHeight((short) (16 * 40));
            row.createCell(i).setCellValue(head[i]);

        }

        //????????????
        for (ProjectTableInfo projectTableInfo : list) {

            //??????????????????(?????????????????????,??????????????????)
            if (projectTableInfo.getKeyProjectStatus() != null) {
                projectTableInfo.setProjectStatus(projectTableInfo.getKeyProjectStatus());
            }


            //???????????????
            List<UserMemberVO> userMemberVOList =
                    userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.PROJECT_GROUP_LEADER.getValue(), projectTableInfo.getId(), JoinStatus.JOINED.getValue());
            StringBuilder students = new StringBuilder("");
            StringBuilder studentsMajorAndGrade = new StringBuilder();
            StringBuilder leaderName = new StringBuilder();
            StringBuilder leaderPhone = new StringBuilder();
            StringBuilder guideTeachers = new StringBuilder();
            for (int i = 0; i < userMemberVOList.size(); i++) {
                UserMemberVO userMemberVO = userMemberVOList.get(i);
                leaderName.append(userMemberVO.getUserName());
                if (userMemberVO.getPhone() != null) {
                    leaderPhone.append(userMemberVO.getPhone());
                }

                students.append(userMemberVO.getUserName());
                students.append("\r\n ");
                studentsMajorAndGrade.append(ConvertUtil.getGradeAndMajorByNumber(userMemberVO.getGrade() + userMemberVO.getMajor()));
                if (i != userMemberVOList.size() - 1) {
                    studentsMajorAndGrade.append("\r\n ");
                }
            }


            //????????????
            List<UserMemberVO> userMemberVOList2 =
                    userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.NORMAL_MEMBER.getValue(), projectTableInfo.getId(), JoinStatus.JOINED.getValue());
            for (int i = 0; i < userMemberVOList2.size(); i++) {
                UserMemberVO userMemberVO = userMemberVOList2.get(i);
                students.append(userMemberVO.getUserName());
                students.append("\r\n ");
                studentsMajorAndGrade.append(ConvertUtil.getGradeAndMajorByNumber(userMemberVO.getGrade() + userMemberVO.getMajor()));
                if (i != userMemberVOList2.size() - 1) {
                    studentsMajorAndGrade.append("\r\n ");
                }
            }

            //????????????
            List<UserMemberVO> userMemberVOList3 =
                    userProjectGroupMapper.selectUserMemberVOListByMemberRoleAndProjectId(MemberRole.GUIDANCE_TEACHER.getValue(), projectTableInfo.getId(), JoinStatus.JOINED.getValue());
            for (int i = 0; i < userMemberVOList3.size(); i++) {
                UserMemberVO userMemberVO = userMemberVOList3.get(i);
                guideTeachers.append(userMemberVO.getUserName());
                guideTeachers.append("\r\n ");
                if (i != userMemberVOList3.size() - 1) {
                    studentsMajorAndGrade.append("\r\n ");
                }
            }


            //?????????
            row = sheet.createRow(index++);

            //????????????
            row.setHeight((short) (16 * 22));
            // ??????
            row.createCell(0).setCellValue(ConvertUtil.getStrCollege(projectTableInfo.getCollege()));
            if (projectTableInfo.getTempSerialNumber() != null) {
                row.createCell(1).setCellValue(projectTableInfo.getTempSerialNumber());
            }
            //????????????
            row.createCell(2).setCellValue(projectTableInfo.getProjectName());
            //????????????
            row.createCell(3).setCellValue(ConvertUtil.getStrExperimentType(projectTableInfo.getExperimentType()));

            row.createCell(4).setCellValue(projectTableInfo.getTotalHours());
            row.createCell(5).setCellValue(guideTeachers.toString());
            row.createCell(6).setCellValue(students.toString());
            row.createCell(7).setCellValue(studentsMajorAndGrade.toString());
            log.info(projectTableInfo.getStartTime().substring(0, 10));
            row.createCell(8).setCellValue(projectTableInfo.getStartTime().substring(0, 10));
            row.createCell(9).setCellValue(projectTableInfo.getEndTime().substring(0, 10));
            row.createCell(10).setCellValue(projectTableInfo.getLabName());
            row.createCell(11).setCellValue(projectTableInfo.getAddress());
            row.createCell(12).setCellValue(leaderName.toString());
            row.createCell(13).setCellValue(leaderPhone.length() == 0 ? "" : leaderPhone.toString());
            row.createCell(14).setCellValue(projectTableInfo.getApplyFunds());
            row.createCell(15).setCellValue(ConvertUtil.getStringSuggestGroupType(projectTableInfo.getSuggestGroupType()));
            row.createCell(16).setCellValue(projectTableInfo.getProjectStatus());
            row.createCell(17).setCellValue(projectTableInfo.getTempSerialNumber());
            row.createCell(18).setCellValue(ConvertUtil.getStrProjectType(projectTableInfo.getProjectType()));

        }

        sheet.createRow(index++).createCell(0).setCellValue("???1????????????????????????????????????????????????2????????????????????????A-F,???????????????????????????");
        index++;

        XSSFRow end = sheet.createRow(index);
        end.createCell(0).setCellValue("??????????????????:");
        end.createCell(3).setCellValue("?????????");
        response.setContentType("application/vnd.ms-excel;charset=utf-8");
        response.setHeader("Content-disposition", "attachment;filename=" + "EstablishExcel" + ".xlsx");
        try {
            OutputStream os = response.getOutputStream();
            wb.write(os);
            os.flush();
            os.close();
        } catch (IOException e) {
            throw new GlobalException(CodeMsg.DOWNLOAD_ERROR);
        }
    }

    @Override
    public synchronized void generateConclusionExcel(HttpServletResponse response) {
        User user = getUserService.getCurrentUser();
        Integer college = user.getInstitute();
        // TODO  ????????????
        // 1.??????HSSFWorkbook?????????HSSFWorkbook????????????Excel??????
        XSSFWorkbook wb = new XSSFWorkbook();
        // 2.???workbook???????????????sheet,??????Excel????????????sheet(?????????)
        XSSFSheet sheet = wb.createSheet("workSheet");

        sheet.setPrintGridlines(true);
        //3.1??????????????????
        XSSFCellStyle cellStyle = wb.createCellStyle();
        //????????????
        cellStyle.setWrapText(true);
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        //??????????????????
        int index = 0;
        //??????
        XSSFRow title = sheet.createRow(index);
        sheet.setColumnWidth(0, 256 * 150);
        title.setHeight((short) (16 * 50));
        title.createCell(index++).setCellValue("?????????????????????" + (getYear() / 100 -1) + "??????" + getYear() + "-" + (getYear() ) + "????????????????????????????????????????????????????????????");


        XSSFRow info = sheet.createRow(index);
        sheet.setColumnWidth(0, 256 * 40);
        info.createCell(0).setCellValue("?????????????????????");


        // 4.1???????????????
        XSSFRow row = sheet.createRow(index++);
        String[] columns = {"??????", "??????", "???????????????", "????????????", "????????????", "????????????"
                , "????????????", "????????????", "????????????", "????????????", "????????????", "????????????", "????????????", "????????????", "????????????", "??????"};
        //??????????????????
        sheet.setColumnWidth(0, 256 * 20);
        for (int i = 0; i < columns.length; i++) {

            // ??????????????????,??????????????????????????????
            row.setHeight((short) (16 * 40));
            row.createCell(i).setCellValue(columns[i]);
        }

        //????????????
        List<ConclusionDTO> list = projectGroupMapper.selectConclusionDTOs(college);

        int count = 0;
        String now = null;
        String before = null;
        User user1 = new User();
        for (ConclusionDTO conclusion : list
        ) {
            // ?????????
            row = sheet.createRow(++index);

            //????????????
            row.setHeight((short) (16 * 22));
            now = conclusion.getId();
            if(!now.equals(before)){
                count++;
                user1 = userMapper.selectByUserCode(String.valueOf(conclusion.getGuideTeacherId()));
            }
            // ??????
            row.createCell(0).setCellValue(count);
            row.createCell(1).setCellValue(ConvertUtil.getStrCollege(conclusion.getCollege()));
            row.createCell(2).setCellValue(conclusion.getLabName());
            row.createCell(3).setCellValue(conclusion.getId());
            row.createCell(4).setCellValue(ConvertUtil.getStrExperimentType(conclusion.getExperimentType()));
            row.createCell(5).setCellValue(conclusion.getTotalHours());
            row.createCell(6).setCellValue(user1.getRealName());
            row.createCell(7).setCellValue(conclusion.getGuideTeacherId());
            row.createCell(8).setCellValue(conclusion.getUserName());
            row.createCell(9).setCellValue(conclusion.getUserId());
            row.createCell(10).setCellValue(ConvertUtil.getStrMemberRole(conclusion.getUserRole()));
            row.createCell(11).setCellValue(ConvertUtil.getMajorNameByNumber(conclusion.getMajor())+conclusion.getGrade()+"???");
            log.info(conclusion.getGrade());
            row.createCell(12).setCellValue(conclusion.getStartTimeAndEndTime());
            row.createCell(13).setCellValue(conclusion.getCheckTime());
            row.createCell(14).setCellValue(ConvertUtil.getProjectRealGrade(conclusion.getCheckResult()));
            before = conclusion.getId();
        }

        sheet.createRow(++index).createCell(0).setCellValue("???1????????????????????????????????????????????????2????????????????????????A-F,???????????????????????????");
        index++;

        XSSFRow end = sheet.createRow(index);
        end.createCell(0).setCellValue("??????????????????:");
        end.createCell(3).setCellValue("?????????");
        response.setContentType("application/vnd.ms-excel;charset=utf-8");
        response.setHeader("Content-disposition", "attachment;filename=" + "Conclusion" + ".xlsx");
        try {
            OutputStream os = response.getOutputStream();
            wb.write(os);
            os.flush();
            os.close();
        } catch (IOException e) {
            throw new GlobalException(CodeMsg.DOWNLOAD_ERROR);
        }

    }

    private void convertDocToPDF(String fileNameOfDoc, String fileNameOfPDF) {
        PDFConvertUtil.convert(fileNameOfDoc, fileNameOfPDF);
    }

    private void mergePdf(String headDocPath, String docPath, String pdfName) {


        String[] docs = new String[2];
        docs[0] = headDocPath;
        docs[1] = docPath;
        PDFMerge.mergePdfFiles(docs, pdfName);
    }

    /**
     * ??????IE edge ???????????? ??????????????????????????????+??????????????????
     *
     * @param fileName ?????????
     * @return
     */
    private static String getFileSuffix(String fileName) {
        if (fileName == null) {
            throw new GlobalException(CodeMsg.FILE_NAME_EMPTY_ERROR);
        }
        //????????????  ?????????"\\",?????????????????????????????????
        int lastIndexOfSlash = fileName.lastIndexOf(".");
        return fileName.substring(lastIndexOfSlash);
    }

}
