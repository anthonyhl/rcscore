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

package com.cmcc.ccs.publicaccount.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The account of model public account
 * @author leizhang
 *
 */
public class AccountModel implements Parcelable {

    private String account = "";
    private String name = "";
    private String portrait = "";
    private String brief_introduction = "";
    private String state = "";
    private String config = "";
    private int isfollow = 0;
    private String _id = "";
    private int idType = 0;
    private int recommend_level = 1;
    /**
     * @param account
     * @param name
     * @param portrait
     * @param brief_introduction
     * @param state
     * @param config
     * @param isfollow
     */
    public AccountModel(String _id,String account, String name, String portrait, String brief_introduction,
            String state, String config, int isfollow) {
        super();
        this._id = _id;
        this.account = account;
        this.name = name;
        this.portrait = portrait;
        this.brief_introduction = brief_introduction;
        this.state = state;
        this.config = config;
        this.isfollow = isfollow;
    }
    /**
     *
     */
    public AccountModel() {
        // TODO Auto-generated constructor stub
    }

    /**
     * create from parcel
     */
    public AccountModel(Parcel parcel) {
        account = parcel.readString();
        name = parcel.readString();
        this._id = parcel.readString();
        brief_introduction = parcel.readString();
        config = parcel.readString();
        portrait = parcel.readString();
        state = parcel.readString();
        isfollow = parcel.readInt();

    }
    /* (non-Javadoc)
     * @see android.os.Parcelable#describeContents()
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /* (non-Javadoc)
     * @see android.os.Parcelable#writeToParcel(android.os.Parcel, int)
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(account);
        dest.writeString(name);
        dest.writeString(getid());
        dest.writeString(brief_introduction);
        dest.writeString(config);
        dest.writeString(portrait);
        dest.writeString(state);
        dest.writeInt(isfollow);
    }

    /**
     * @return the account
     */
    public String getAccount() {
        return account;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the portrait
     */
    public String getPortrait() {
        return portrait;
    }

    /**
     * @return the brief_introduction
     */
    public String getBrief_introduction() {
        return brief_introduction;
    }

    /**
     * @return the state
     */
    public String getState() {
        return state;
    }

    /**
     * @return the config
     */
    public String getConfig() {
        return config;
    }

    /**
     * @return the isfollow
     */
    public int getIsfollow() {
        return isfollow;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "AccountModel [account=" + account + ", name=" + name + ", portrait=" + portrait
                + ", brief_introduction=" + brief_introduction + ", state=" + state + ", config="
                + config + ", isfollow=" + isfollow + ", _id=" + getid() + "]";
    }

    /**
     * @return the _id
     */
    public String getid() {
        return _id;
    }
    /**
     * @return the _id
     */
    public String get_id() {
        return _id;
    }
    /**
     * @param _id the _id to set
     */
    public void set_id(String _id) {
        this._id = _id;
    }
    /**
     * @param account the account to set
     */
    public void setAccount(String account) {
        this.account = account;
    }
    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }
    /**
     * @param portrait the portrait to set
     */
    public void setPortrait(String portrait) {
        this.portrait = portrait;
    }
    /**
     * @param brief_introduction the brief_introduction to set
     */
    public void setBrief_introduction(String brief_introduction) {
        this.brief_introduction = brief_introduction;
    }
    /**
     * @param state the state to set
     */
    public void setState(String state) {
        this.state = state;
    }
    /**
     * @param config the config to set
     */
    public void setConfig(String config) {
        this.config = config;
    }
    /**
     * @param isfollow the isfollow to set
     */
    public void setIsfollow(int isfollow) {
        this.isfollow = isfollow;
    }
    /**
     * @return the idType
     */
    public int getIdType() {
        return idType;
    }
    /**
     * @param idType the idType to set
     */
    public void setIdType(int idType) {
        this.idType = idType;
    }
    /**
     * @return the recommend_level
     */
    public int getRecommend_level() {
        return recommend_level;
    }
    /**
     * @param recommend_level the recommend_level to set
     */
    public void setRecommend_level(int recommend_level) {
        this.recommend_level = recommend_level;
    }

}
