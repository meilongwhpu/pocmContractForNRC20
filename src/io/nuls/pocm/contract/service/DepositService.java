package io.nuls.pocm.contract.service;

import io.nuls.contract.sdk.Block;
import io.nuls.pocm.contract.model.DepositDetailInfo;
import io.nuls.pocm.contract.model.DepositInfo;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static io.nuls.contract.sdk.Utils.require;

/**
 * @author: Long
 * @date: 2019-03-15
 */
public class DepositService {

    /**
     * 最短锁定区块（参数类型为数字，XXXXX块后才可退出抵押）
     */
    private int minimumLocked;

    /**
     * 最低抵押na数量(1亿个na等于1个NULS）
     */
    private BigInteger minimumDeposit;

    /**
     * 最大抵押地址数量（可选参数）
     */
    private int maximumDepositAddressCount;

    /**
     * 用户抵押信息(key为抵押者地址）
     */
    private Map<String, DepositInfo> depositUsers = new HashMap<String, DepositInfo>();


    /**
     * 总抵押地址数量
     */
    private int totalDepositAddressCount = 0;

    /**
     * 总抵押金额
     */
    private BigInteger totalDeposit = BigInteger.ZERO;


    public DepositService(int minimumLocked, BigInteger minimumDeposit, int maximumDepositAddressCount) {
        this.minimumLocked = minimumLocked;
        this.minimumDeposit = minimumDeposit;
        this.maximumDepositAddressCount = maximumDepositAddressCount;
    }

    public Map<String, DepositInfo> getDeposit() {
        return depositUsers;
    }

    public DepositInfo getDepositInfo(String userAddress) {
        return depositUsers.get(userAddress);
    }

    public DepositInfo addDeposit(String depositAddress, String miningAddress, BigInteger depositValue, long currentHeight, long depositNumber) {
        require(depositValue.compareTo(minimumDeposit) >= 0, "未达到最低抵押值:" + minimumDeposit);
        DepositInfo info = depositUsers.get(depositAddress);
        if (info == null) {
            if (maximumDepositAddressCount > 0) {
                require(totalDepositAddressCount + 1 <= maximumDepositAddressCount, "超过最大抵押地址数量");
            }
            info = new DepositInfo();
            totalDepositAddressCount += 1;
        }
        DepositDetailInfo detailInfo = new DepositDetailInfo();
        detailInfo.setDepositAmount(depositValue);
        detailInfo.setDepositHeight(currentHeight);
        detailInfo.setMiningAddress(miningAddress);
        detailInfo.setDepositNumber(depositNumber);
        info.setDepositorAddress(depositAddress);
        info.getDepositDetailInfos().put(depositNumber, detailInfo);
        info.setDepositTotalAmount(info.getDepositTotalAmount().add(depositValue));
        info.setDepositCount(info.getDepositCount() + 1);
        depositUsers.put(depositAddress, info);
        totalDeposit = totalDeposit.add(depositValue);
        return info;
    }

    public void removeDeposit(String userAddress) {
        totalDepositAddressCount -= 1;
        depositUsers.remove(userAddress);

    }

    /**
     * 检查抵押是否在锁定中
     *
     * @param depositInfo
     * @return
     */
    public long checkAllDepositLocked(DepositInfo depositInfo) {
        long result;
        Map<Long, DepositDetailInfo> infos = depositInfo.getDepositDetailInfos();
        for (Long key : infos.keySet()) {
            result = checkDepositLocked(infos.get(key));
            if (result != -1) {
                return result;
            }
        }
        return -1;
    }

    /**
     * 检查抵押是否在锁定中
     *
     * @param detailInfo
     * @return
     */
    public long checkDepositLocked(DepositDetailInfo detailInfo) {
        long currentHeight = Block.number();
        long unLockedHeight = detailInfo.getDepositHeight() + minimumLocked + 1;
        if (unLockedHeight > currentHeight) {
            // 锁定中
            return unLockedHeight;
        }
        //已解锁
        return -1;
    }

    /**
     * 清楚抵押信息中的详细抵押信息
     *
     * @param info
     */
    public void clearDepositDetailInfos(DepositInfo info) {
        info.getDepositDetailInfos().clear();
        info.setDepositCount(0);
        info.setDepositTotalAmount(BigInteger.ZERO);
    }


    public int getMinimumLocked() {
        return minimumLocked;
    }


    public BigInteger getMinimumDeposit() {
        return minimumDeposit;
    }

    public int getMaximumDepositAddressCount() {
        return maximumDepositAddressCount;
    }

    public BigInteger getTotalDeposit() {
        return totalDeposit;
    }

    public void setTotalDeposit(BigInteger totalDeposit) {
        this.totalDeposit = totalDeposit;
    }

    public int getTotalDepositAddressCount() {
        return totalDepositAddressCount;
    }

    public void setTotalDepositAddressCount(int totalDepositAddressCount) {
        this.totalDepositAddressCount = totalDepositAddressCount;
    }


}
