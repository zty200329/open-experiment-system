package com.swpu.uchain.openexperiment.controller;

import com.swpu.uchain.openexperiment.cas.CasAutoConfig;
import com.swpu.uchain.openexperiment.domain.Role;
import com.swpu.uchain.openexperiment.domain.User;
import com.swpu.uchain.openexperiment.enums.CodeMsg;
import com.swpu.uchain.openexperiment.form.user.GetAllPermissions;
import com.swpu.uchain.openexperiment.mapper.UserRoleMapper;
import com.swpu.uchain.openexperiment.result.Result;
import com.swpu.uchain.openexperiment.security.JwtTokenUtil;
import com.swpu.uchain.openexperiment.security.JwtUser;
import com.swpu.uchain.openexperiment.service.AclService;
import com.swpu.uchain.openexperiment.service.GetUserService;
import com.swpu.uchain.openexperiment.service.RoleService;
import com.swpu.uchain.openexperiment.service.UserService;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jasig.cas.client.authentication.AttributePrincipal;
import org.jasig.cas.client.util.AbstractCasFilter;
import org.jasig.cas.client.validation.Assertion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Array;
import java.net.URLEncoder;
import java.util.*;

@Controller
@Slf4j
@RequestMapping("/casToHomePage")
public class CASController{

	@Autowired
	private CasAutoConfig autoconfig;
	@Autowired
	private GetUserService getUserService;
	@Autowired
	private UserService userService;
	@Autowired
	private UserRoleMapper userRoleMapper;
	@Autowired
	private AuthenticationManager authenticationManager;
	@Autowired
	private AclService aclService;
	@Autowired
	private JwtTokenUtil jwtTokenUtil;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private RoleService roleService;

	/**
	 * ??????????????? ??????
	 *
	 * @return
	 */
	@RequestMapping(value = "/inCAS",method = {RequestMethod.GET,RequestMethod.POST})
	public Object list(HttpSession session) {
//		get();
		// ???????????????cas????????? ????????????
		Assertion assertion=
				(Assertion)
						session.getAttribute(AbstractCasFilter.CONST_CAS_ASSERTION);
		/*?????????????????????????????????
		 *???UIA?????????????????????????????????
		 *(1)???????????????????????????????????????
		 *(2)????????????????????????????????????*/
		String ssoId = assertion.getPrincipal().getName();
		List<Integer> rolesList = getAllPermissions(ssoId);


		if(rolesList != null){
			Integer role1 = Collections.min(rolesList);
			User user = getUserService.selectByUserCodeAndRole(ssoId,role1);
			//??????????????????????????????????????????
			if (user == null) {
				return Result.error(CodeMsg.USER_NO_EXIST);
			}
			Authentication token = new UsernamePasswordAuthenticationToken(user.getCode(), user.getPassword());
			Authentication authentication = authenticationManager.authenticate(token);
			//???????????????????????????
			SecurityContextHolder.getContext().setAuthentication(authentication);
			final UserDetails userDetails;

			User user1 = getUserService.selectByUserCode(user.getCode());
			if (user1==null) {
				throw new UsernameNotFoundException(String.format(" user not exist with stuId ='%s'.", user.getCode()));
			} else {
				//??????????????????userDetails??????
				List<String> aclUrl = aclService.getUserAclUrl(Long.valueOf(user.getCode()));
				userDetails =new JwtUser(user.getCode(), passwordEncoder.encode(user.getPassword()), aclUrl);
			}
			log.info("?????????????????????userDetails: {}", userDetails);
			//???????????????token
			final String realToken = jwtTokenUtil.generateToken(userDetails);
			Role role = roleService.getUserRoles(Long.valueOf(user1.getCode()), Long.valueOf(role1));

			// ?????????????????????
			return new RedirectView(autoconfig.getUiRrl()+"?userToken="+realToken+"&role="+role1);
		}else{
			return new RedirectView(autoconfig.getUiRrl());
		}




	}
	public static void get() {
		try {
			String requestPath = "http://uoep.swpu.edu.cn:8083/logout";
			HttpClient httpClient = new HttpClient();
			GetMethod get = new GetMethod(requestPath);
			int status = httpClient.executeMethod(get);
			if (status == HttpStatus.SC_OK) {
				System.out.println("GET???????????????" + new String(get.getResponseBody()));
			} else {
				System.out.println("GET??????????????????" + status);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private  List<Integer> getAllPermissions(String stuId) {
		List<Integer> roles = userRoleMapper.selectUserRolesById(Long.valueOf(stuId));
		return roles;
	}

	/**
	 * ???SSO???????????????String??????????????????????????????List??????
	 * @param str ??????????????????String
	 * @return List
	 */
	private List<Map<String,String>> parseStringToList(
			String str){
		List<Map<String,String>> list =
				new ArrayList<Map<String,String>>();
		if(str == null || str.equals("")){
			return list;
		}
		String[] array = str.split("-");
		for (String subArray : array) {
			String[] keyResult = subArray.split(",");
			Map<String,String> map =
					new HashMap<String, String>();
			for (String subResult : keyResult) {
				String[] value = subResult.split(":");
				map.put(value[0], value[1]);
			}
			list.add(map);
		}
		return list;
	}


}