/*******************************************************************************
 * Software Name: RCS
 *
 * Copyright (C) 2015 TCL Telecommunication Technology Ltd.
 *
 * Description: TODO
 *
 * Feb 10, 2015
 *
 *Author: lei.zhang9282@icloud.com
 *
 * Modification record:
 *-------------------------------------------------------------------------------------------
 * | Feb 10, 2015 | init it.                                                                     |
 *-------------------------------------------------------------------------------------------
 ******************************************************************************/

package com.cmcc.ccs.publicaccount.api;

import java.util.Map;
import java.util.Set;

import android.os.Parcelable;

/**
 * This interface define the API of public account.
 * <BR>
 * please reference <b>S15</b> <b>5.2.2.1</b>
 * @author leizhang
 *
 */
public interface IPublicAccountApi {

    public static final class Params{
        //Access Token///////////////////////
        /**
         * see S18 6.4.1.3
         */
        public static final String AUTHORIZATION = "Authorization";
        public static final String X_3GPP_INTENDED_IDENTITY = "X-3GPP-Intended-Identity";
    }

    /**
     * this class define the recommend type of recommend public account
     * @author leizhang
     *
     */
    public static final class RecommendType {
        public static final int TYPE_HOT = 1;
        public static final int TYPE_INFORMATION_READ = 2;
        public static final int TYPE_STAR = 3;
        public static final int TYPE_CATER_BUY = 4;
        public static final int TYPE_COMPANY_ORG = 5;
        public static final int TYPE_FRIENR = 6;
        public static final int TYPE_ENTERTAINMENT = 7;
        public static final int TYPE_MUSIC_VIDEO = 8;
        public static final int TYPE_EDUCATION = 9;
        public static final int TYPE_OTHER = 99;
    }

    /**
     * report type.See @{link complainPublic}
     * @author leizhang
     *
     */
    public static final class ComplainType{
        public static final int TYPE_ACCOUNT = 1;//complain account
        public static final int TYPE_CONTENT = 2;//complain the received content
    }

    /**
     *this class define the error code
     * @author leizhang
     *
     */
    public static final class ResultCode{
        /**
         * 处理成功
         */
        public static int _000000 = 0;
        /**
         * 未知错误
         */
        public static int _100000 = 100000;
        /**
         * 系统忙
         */
        public static int _100001 = 100001;
        /**
         * 操作超时
         */
        public static int _100002 = 100002;
        /**
         * 网络异常
         */
        public static int _100003 = 100003;
        /**
         * 数据库操作异常
         */
        public static int _100004 = 100004;
        /**
         * 相关配置项不存在
         */
        public static int _100005 = 100005;
        /**
         * 非法的连接源IP地址
         */
        public static int _100006 = 100006;
        /**
         * IP地址不匹配
         */
        public static int _100007 = 100007;
        /**
         * 未授权的接口调用
         */
        public static int _100008 = 100008;
        /**
         * 业务请求已超过SLA请求速率上限
         */
        public static int _100009 = 100009;
        /**
         * 设备的业务请求已超SLA请求速率上限
         */
        public static int _100010 = 100010;
        /**
         * 路由异常
         */
        public static int _100011 = 100011;
        /**
         * 分页码错误
         */
        public static int _100012 = 100012;
        /**
         * 每页条数错误
         */
        public static int _100013 = 100013;

        /**
         * 系统未初始化
         */
        public static int _100101 = 100101;
        /**
         * 未知错误
         */
        public static int _200000 = 200000;
        /**
         * 必选参数为空
         */
        public static int _200001 = 200001;
        /**
         * 参数格式错误
         */
        public static int _200002 = 200002;
        /**
         * 参数长度超出范围
         */
        public static int _200003 = 200003;
        /**
         * 所有输入参数都为空
         */
        public static int _200004 = 200004;
        /**
         * 消息版本号非法
         */
        public static int _200005 = 200005;
        /**
         * 平台编码不存在
         */
        public static int _200006 = 200006;
        /**
         * 时间戳非法
         */
        public static int _200007 = 200007;
        /**
         * 消息名称错误
         */
        public static int _200008 = 200008;
        /**
         * 消息解析失败
         */
        public static int _200009 = 200009;
        /**
         * 区域编码非法
         */
        public static int _200010 = 200010;
        /**
         * (FTP接口)文件头非法
         */
        public static int _200101 = 200101;
        /**
         * (FTP接口)文件尾非法
         */
        public static int _200102 = 200102;
        /**
         * (FTP接口)文件校验失败
         */
        public static int _200103 = 200103;
        /**
         * 未知错误
         */
        public static int _300000 = 300000;
        /**
         * 用户不存在
         */
        public static int _301001 = 301001;
        /**
         * 用户已经存在
         */
        public static int _301002 = 301002;
        /**
         * 密码错误
         */
        public static int _301003 = 301003;
        /**
         * 新旧密码相同
         */
        public static int _301004 = 301004;
        /**
         * 验证码过期
         */
        public static int _301005 = 301005;
        /**
         * 校验验证码失败
         */
        public static int _301006 = 301006;
        /**
         * 未生成验证码
         */
        public static int _301008 = 301008;
        /**
         * 电话号码格式错误
         */
        public static int _301009 = 301009;
        /**
         * 号段不存在
         */
        public static int _301010 = 301010;
        /**
         * 用户状态错误，不能获取验证码
         */
        public static int _301011 = 301011;
        /**
         * 用户状态异常
         */
        public static int _301012 = 301012;
        /**
         * 用户已经欠费
         */
        public static int _301013 = 301013;
        /**
         * 用户不允许进行此操作
         */
        public static int _301014 = 301014;
        /**
         * 用户取回密码超过次数限制
         */
        public static int _301015 = 301015;
        /**
         * 地区编码不存在
         */
        public static int _301017 = 301017;
        /**
         * 向用户发送短信失败
         */
        public static int _301018 = 301018;
        /**
         * 注册正在处理
         */
        public static int _301019 = 301019;
        /**
         * 用户当前状态不能注册
         */
        public static int _301020 = 301020;
        /**
         * 用户当前状态不能注销
         */
        public static int _301021 = 301021;
        /**
         * 用户未登录
         */
        public static int _301022 = 301022;
        /**
         * 用户没有权限
         */
        public static int _301023 = 301023;
        /**
         * 昵称未设置
         */
        public static int _301024 = 301024;
        /**
         * 昵称已经存在
         */
        public static int _301025 = 301025;
        /**
         * 手机号码已被账号绑定
         */
        public static int _301026 = 301026;
        /**
         * 邮箱已被其他账号绑定
         */
        public static int _301027 = 301027;
        /**
         * 邮件最大重发次数配置错误
         */
        public static int _301028 = 301028;
        /**
         * 邮件重发次数达到上限，邮件下发失败
         */
        public static int _301029 = 301029;
        /**
         * 用户注册失败
         */
        public static int _301030 = 301030;
        /**
         * 用户登陆失败
         */
        public static int _301031 = 301031;
        /**
         * 用户注销失败
         */
        public static int _301032 = 301032;
        /**
         * 邮箱未激活
         */
        public static int _301033 = 301033;
        /**
         * 动态密码过期
         */
        public static int _301034 = 301034;
        /**
         * 校验动态密码失败
         */
        public static int _301035 = 301035;
        /**
         * 重复绑定
         */
        public static int _301036 = 301036;
        /**
         * 取消绑定失败
         */
        public static int _301037 = 301037;
        /**
         * 修改用户信息失败
         */
        public static int _301038 = 301038;
        /**
         * 提醒请求文件不存在
         */
        public static int _301101 = 301101;
        /**
         * 提醒信息已经过期
         */
        public static int _301102 = 301102;
        /**
         * 提醒信息开关关闭
         */
        public static int _301103 = 301103;
        /**
         * 提醒文件文件头异常
         */
        public static int _301104 = 301104;
        /**
         * 提醒文件文件尾异常
         */
        public static int _301105 = 301105;
        /**
         * 会话失效
         */
        public static int _301106 = 301106;
        /**
         * 手机号邮箱仅能输入一个
         */
        public static int _301107 = 301107;
        /**
         * 会员开通记录为空
         */
        public static int _301108 = 301108;
        /**
         * 含有敏感词
         */
        public static int _211104 = 211104;
        /**
         * 不存在的公众账号
         */
        public static int _304001 = 304001;
        /**
         * 不合法的公众账号
         */
        public static int _304002 = 304002;
        /**
         * 连接第三方公众账号超时
         */
        public static int _304003 = 304003;
        /**
         * 不合法的第三方公众账号应用地址或错误Token
         */
        public static int _304004 = 304004;
        /**
         * 第三方公众账号应用服务故障
         */
        public static int _304005 = 304005;
        /**
         * 不合法的订阅，已存在订阅关系
         */
        public static int _304006 = 304006;
        /**
         * 不合法的退订，不存在订阅关系
         */
        public static int _304007 = 304007;
        /**
         * 更新PCC失败，更新用户信息失败
         */
        public static int _304008 = 304008;
        /**
         * 更新PCC失败，更新订阅关失败
         */
        public static int _304009 = 304009;
        /**
         * 消息不存在
         */
        public static int _304010 = 304010;
        /**
         * 查询消息失败
         */
        public static int _304011 = 304011;
        /**
         * 公众账号查询失败
         */
        public static int _304012 = 304012;
        /**
         * 设置消息状态失败
         */
        public static int _304013 = 304013;
        /**
         * 消息删除失败
         */
        public static int _304014 = 304014;
        /**
         * 订阅关系查询失败
         */
        public static int _304015 = 304015;
        /**
         * 订阅关系数统计失败
         */
        public static int _304016 = 304016;
        /**
         * 推送消息含有敏感词
         */
        public static int _304100 = 304100;
        /**
         * 消息源推送超过SLA限制
         */
        public static int _304101 = 304101;
        /**
         * 保存推送消息失败
         */
        public static int _304102 = 304102;
        /**
         * 对接第三方异常
         */
        public static int _304103 = 304103;
        /**
         * 消息接受方ID非法
         */
        public static int _304104 = 304104;
        /**
         * 消息源状态非法
         */
        public static int _304105 = 304105;
        /**
         * 消息源订阅状态不正常
         */
        public static int _304106 = 304106;
        /**
         * 订阅用户不存在
         */
        public static int _304107 = 304107;
        /**
         * 当前不支持用户-用户的p2p
         */
        public static int _304108 = 304108;
    }

    public static final class APIDescription {
        public static final String METHOD_ADDSUBSCRIBE = "addsubscribe";
        public static final String METHOD_CANCELSUBSCRIBE = "cancelsubscribe";
        public static final String METHOD_GETPUBLICLIST = "getpubliclist";
        public static final String METHOD_GETPUBLICDETAIL = "getpublicdetail";
        public static final String METHOD_QUERYUSERSUB = "queryusersub";
        public static final String METHOD_GETPUBLICMENU = "getpublicmenu";
        public static final String METHOD_GETPREMESSAGE = "getpremessage";
        public static final String METHOD_COMPLAINPUBLIC = "complainpublic";
        public static final String METHOD_GETPUBLICRECOMMEND = "getpublicrecommend";
        public static final String METHOD_SETACCEPTSTATUS = "setacceptstatus";
    }

    /**
     * this class wrap the response by server
     * @author leizhang
     *
     */
    public class ResultWrap{
        public String method_name;//method name for callback
        public String lastXml ;//the content xml
        public int code;//the error code
        public String desc;//the description of error code
        public Map<String, String> otherParam = null;//the extras,for example, pa_uuid
    }
    /**
     * refer <b>5.2.2.4.1.3</b>
     * @param pa_uuid the identity of the follow
     * @return the same as pa_uuid
     */
    public String subscribe(String pa_uuid);

    /**
     * refer <b>5.2.2.4.2.3</b>
     * @param pa_uuid he identity of the unfollow
     * @return the same as pa_uuid
     */
    public String cancelSubscribe(String pa_uuid);

    /**
     * see <b>5.2.2.4.4.2</b>
     * @param keyword the keyword,can not be empty
     * @param order the order columns,0 is default.0 indicates the timestamp of follow,1 indicates the name
     * @param pageSize the page size
     * @param pageNum  page Index
     * @return the list of public account
     */
    public Set<Parcelable> getPublicList(String keyword,int order,int pageSize, int pageNum);

    /**
     * See <b>5.2.2.4.6.2</b>
     * @param pa_uuid the identity of the account
     * @param updateTime keep this time on UA,use UTC+8
     * @return the detail of public account which indicates by pa_uuid
     */
    public Parcelable getPublicDetail(String pa_uuid,String updateTime);

    /**
     * see <b>5.2.2.4.3</b>
     * @param order the order columns,0 is default.0 indicates the timestamp of follow,1 indicates the name
     * @param pageSize the page size
     * @param pageNum  page Index,start with 1
     * @return the list of followed public account
     */
    public Set<Parcelable> queryUserSub(int order,int pageSize, int pageNum);

    /**
     * see <b>5.2.2.4.7</b>
     * @param pa_uuid he identity of the account
     * @param menutimeStamp keep this time on UA,use UTC+8
     * @return the pa_uuid,menu array,menutimeStamp
     */
    public Parcelable getPublicMenu(String pa_uuid,String menutimeStamp);

    /**
     * see <b>5.2.2.4.8.1</b>
     * @param pa_uuid the identity of this account
     * @param timeStamp keep this time on UA,use UTC+8
     * @param order the order columns,0 is default.0 indicates the timestamp of follow,1 indicates the name
     * @param pageSize the page size
     * @param pageNum  page Index
     * @return the pa_uuid,the msgList
     */
    public Parcelable getPreMessage(String pa_uuid,String timeStamp,int order,int pageSize, int pageNum);

    /**
     * see <b>5.2.2.4.9.2<b/>
     * @param pa_uuid the identity of this account
     * @param type see @{link ComplainType}
     * @param reason the reason
     * @param data see doc
     * @param description user input
     * @return the pa_uuid
     */
    public String complainPublic(String pa_uuid,int type,String reason,String data,String description);

    /**
     * reference <b>5.2.2.4.5</b>
     * @param type See {@link RecommendType}
     * @param pageSize the page size
     * @param pageNum  page Index
     * @return the list of recommend public account
     */
    public Set<Parcelable> getPublicRecommend(int type,int pageSize, int pageNum);

    /**
     * FIXME TODO
     * see <b>5.2.2.4.10</b>
     * @param pa_uuid the identity of this account
     * @param acceptStatus 1 accepted,0 not accepted
     * @return the pa_uuid & result code
     */
    public Parcelable setAcceptState(String pa_uuid,int acceptStatus);

    /**
     * this method used to authorization when first connect the server or reconnect
     */
    public void Authorization();
}
