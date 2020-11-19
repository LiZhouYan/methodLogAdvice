package com.xxxxx.common.advice;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.xxxx.beans.data.ApiRequestData;
import com.xxxx.beans.data.ApiResponseData;
import com.xxxx.utils.http.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Objects;

/**
 * @version 1.0
 * @Desc    接口访问入参和出参信息打印
 * @Author zhouguanglin
 * @Email zhouguanglin_java@163.com
 * @Date 2020/9/15 5:06 下午
 */
@Component
@Aspect
@Slf4j
public class MethodLogAdvice {
    private static final String UNKNOWN_PARAM_CLASS_NAME = "unknown";

    /**
     * 切入点
     */
    @Around("@annotation(org.springframework.web.bind.annotation.RequestMapping)")
    public Object aroundAdvice(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        long startTime = System.currentTimeMillis();
        // 记录请求数据日志
        methodRequestData(signature, method, args);
        // 实际执行函数
        Object objectReturn = null;
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(void.class)) {
            joinPoint.proceed();
        } else {
            objectReturn = joinPoint.proceed();
        }
        // 记录返回数据日志
        methodResponseData(method, objectReturn, startTime);

        return objectReturn;
    }

    /**
     * 日志记录函数返回值
     *  @param method
     * @param objectReturn
     * @param startTime
     */
    private void methodResponseData(Method method, Object objectReturn, long startTime) {
        try {
            if (objectReturn != null) {
                Object object = objectReturn;
                ApiResponseData apiResponseData = new ApiResponseData();
                apiResponseData.setClassName(method.getDeclaringClass().getName());
                apiResponseData.setMethod(method.getName());
                apiResponseData.setData(JSONObject.toJSON(object));
                apiResponseData.setConsumeTime(System.currentTimeMillis()-startTime);
                log.info("\r\n>>>>>接口返回数据>>>>>:{}",apiResponseData.toString());
            }
        } catch (Throwable e) {
            log.warn("返回数据出现异常<<<<<<<<<<<<<<<" + e.getMessage(), e);
        }
    }

    /**
     * 记录请求数据日志
     *
     * @param signature
     * @param method
     * @param args
     */
    private void methodRequestData(MethodSignature signature, Method method, Object[] args) {
        String methodName = method.getName();
        try {
            ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = servletRequestAttributes == null ? null : servletRequestAttributes.getRequest();
            requestCookies(request);
            Annotation[][] paramAnnotations = method.getParameterAnnotations();
            Class<?>[] parameterTypes = method.getParameterTypes();
            String argsString = null;
            //参数不为空，若为空，可能为GET，需request.QueryString
            if (ArrayUtils.isNotEmpty(args)) {
                //遍历参数
                for (int i = 0; i < args.length; i++) {
                    Object object = args[i];
                    Annotation paramAnnotation = null;
                     //获取参数注解 TODO 需要改进具体的注解
                    if (paramAnnotations != null && paramAnnotations.length > i) {
                        if (paramAnnotations[i].length == 1) {
                            paramAnnotation = paramAnnotations[i][0];
                        }else if (paramAnnotations[i].length > 1) {
                            paramAnnotation = paramAnnotations[i][1];
                        }
                    }
                    //获取参数类型
                    Class<?> paramClass = null;
                    if (parameterTypes != null && parameterTypes.length > i) {
                        paramClass = parameterTypes[i];
                    }
                    //获取参数名称
                    String paramClassSimpleName = paramClass != null ? paramClass.getSimpleName() : UNKNOWN_PARAM_CLASS_NAME;
                    //打印参数名
                    StringBuilder argsBuilder = new StringBuilder();
                    if (paramAnnotation != null) {
                        if (paramAnnotation instanceof PathVariable) {
                            PathVariable pathVariable = (PathVariable) paramAnnotation;
                            argsBuilder.append("[PathVariable] ").append(paramClassSimpleName).append(" ").append(pathVariable.value());
                        } else if (paramAnnotation instanceof RequestParam) {
                            RequestParam requestParam = (RequestParam) paramAnnotation;
                            argsBuilder.append("[RequestParam] ").append(paramClassSimpleName).append(" ").append(requestParam.value());
                        } else {
                            argsBuilder.append(paramClassSimpleName);
                        }
                    } else {
                        argsBuilder.append(paramClassSimpleName);
                    }
                    argsBuilder.append(":");
                    //获取参数值 校验参数 HttpServletRequest、HttpServletResponse、HttpSession、JSONObject、JSONArray、Object
                    if (object instanceof HttpServletRequest) {
                        HttpServletRequest paramRequest = (HttpServletRequest) object;
                        Enumeration<String> paramKeys = paramRequest.getParameterNames();
                        while (paramKeys.hasMoreElements()) {
                            String paramKey = paramKeys.nextElement();
                            argsBuilder.append("\r\n\t\t");
                            argsBuilder.append(paramKey).append(" : ");
                            String paramValue = paramRequest.getParameter(paramKey);
                            argsBuilder.append(paramValue);
                        }
                    } else if (Objects.equals(paramClass, HttpServletResponse.class)) {
                        argsBuilder.append("response");
                    } else if (Objects.equals(paramClass, HttpSession.class)) {
                        argsBuilder.append("session");
                    } else if (object instanceof JSONObject) {
                        argsBuilder.append(((JSONObject) object).toJSONString());
                    } else if (object instanceof JSONArray) {
                        argsBuilder.append(((JSONArray) object).toJSONString());
                    } else {
                        argsBuilder.append(JSONObject.toJSONString(object));
                    }
                    argsString = argsBuilder.toString();
                }
            } else {
                //请求参数
                argsString = request.getQueryString();
            }
            ApiRequestData apiRequestData = new ApiRequestData();
            apiRequestData.setUri(request.getRequestURI());
            apiRequestData.setType(request.getMethod());
            apiRequestData.setRemoteAddress(String.format("RemoteAddress1：%s ,RemoteAddress2： %s", HttpUtils.getRemoteAddress(request), request.getRemoteAddr()));
            apiRequestData.setClassName(method.getDeclaringClass().getName());
            apiRequestData.setMethod(methodName);
            apiRequestData.setParams(argsString);
            log.info("\r\n>>>>接口请求数据>>>>:{}",apiRequestData.toString());
        } catch (Throwable e) {
            log.warn("记录接收参数转换出现异常<<<<<<<<<<<<<<<" + e.getMessage(), e);
        }
    }

    /**
     * 请求cookie
     * @param request
     */
    private void requestCookies(HttpServletRequest request) {
        if (request != null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null && cookies.length > 0) {
                StringBuilder cookieInfo = new StringBuilder();
                cookieInfo.append("\r\nrequest cookies : ");
                for (Cookie cookie : cookies) {
                    cookieInfo.append(
                            "\r\n\tcookie [ " + cookie.getName() + " ] = " + cookie.getValue());
                }
                log.debug(cookieInfo.toString());
            }
        }
    }


}
