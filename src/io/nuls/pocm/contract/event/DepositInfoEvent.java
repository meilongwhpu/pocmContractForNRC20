package io.nuls.pocm.contract.event;

import io.nuls.contract.sdk.Event;
import io.nuls.pocm.contract.model.DepositInfo;

/**
 * Mortgage Information Event
 *
 * @author: Long
 * @date: 2019-03-15
 */
public class DepositInfoEvent extends DepositInfo implements Event {

    public DepositInfoEvent(DepositInfo info) {
        super(info);
    }

}
