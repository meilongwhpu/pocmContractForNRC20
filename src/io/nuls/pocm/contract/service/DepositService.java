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
     * Minimum locking height (parameter type is digital, XX height can be withdrawn from mortgage)
     */
    private int minimumLocked;

    /**
     * Number of minimum mortgage Na (100 million Na equals 1 NULS)
     */
    private BigInteger minimumDeposit;

    /**
     * the maximum number of mortgage addresses
     */
    private int maximumDepositAddressCount;

    /**
     * User Mortgage Information (key is the mortgagor's address)
     */
    private Map<String, DepositInfo> depositUsers = new HashMap<String, DepositInfo>();


    /**
     * Number of mortgages
     */
    private int totalDepositAddressCount = 0;

    /**
     * Total Mortgage Amount
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

    /**
     * Adding Mortgage Information to Mortgage Queue (depositUsers)
     *
     * @param depositAddress Mortgage address
     * @param miningAddress  Receiving Token Address
     * @param depositValue   Amount of mortgage
     * @param currentHeight  Current height
     * @param depositNumber  Mortgage number
     * @return Mortgage information
     */
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
     * Check if all mortgages of the user are locked
     *
     * @param depositInfo Mortgage information
     * @return -1:locking
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
     * Check if the mortgage is locked
     *
     * @param detailInfo Mortgage detail information
     * @return -1:locking
     */
    public long checkDepositLocked(DepositDetailInfo detailInfo) {
        long currentHeight = Block.number();
        long unLockedHeight = detailInfo.getDepositHeight() + minimumLocked + 1;
        if (unLockedHeight > currentHeight) {
            // locking
            return unLockedHeight;
        }
        //unlocked
        return -1;
    }

    /**
     * clear detailed mortgage information from mortgage information
     *
     * @param info Mortgage information
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


}
