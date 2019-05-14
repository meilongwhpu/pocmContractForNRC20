package io.nuls.pocm.contract.service;

import io.nuls.pocm.contract.model.AirdropperInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Long
 * @date: 2019-03-15
 */
public class AriDropperService {

    /**
     * 接收空投地址列表
     */
    private List<AirdropperInfo> ariDropperInfos = new ArrayList<AirdropperInfo>();

    /**
     * 添加空投记录
     *
     * @param info
     */
    public void addAriDropperInfo(AirdropperInfo info) {
        ariDropperInfos.add(info);
    }

    /**
     * 获取空投列表记录
     *
     * @return
     */
    public List<AirdropperInfo> getAriDropperInfos() {
        return ariDropperInfos;
    }

}
