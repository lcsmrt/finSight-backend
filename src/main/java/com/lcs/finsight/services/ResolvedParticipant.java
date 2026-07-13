package com.lcs.finsight.services;

import com.lcs.finsight.models.User;

import java.math.BigDecimal;

/** A resolved (member, share) pair, produced by {@link SplitResolver} and stamped onto transactions. */
record ResolvedParticipant(User member, BigDecimal shareAmount) {}
