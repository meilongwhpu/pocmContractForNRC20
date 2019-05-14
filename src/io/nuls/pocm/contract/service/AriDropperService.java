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
     * Receiving Airdrop Address List
     */
    private List<AirdropperInfo> ariDropperInfos = new ArrayList<AirdropperInfo>();

    /**
     * Adding Airdrop Records
     *
     * @param info
     */
    public void addAriDropperInfo(AirdropperInfo info) {
        ariDropperInfos.add(info);
    }

    /**
     * Query airdrop records
     *
     * @return
     */
    public List<AirdropperInfo> getAriDropperInfos() {
        return ariDropperInfos;
    }

}
