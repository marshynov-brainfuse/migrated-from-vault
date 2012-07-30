package com.eggtheory.brainfuse.server.payroll;

import com.eggtheory.brainfuse.bizobjects.user.PayrollActivity;

public interface PayrollProvider {

	public PayrollActivity createTransaction() throws Exception;
	
	public PayrollActivity getTransactionsDetails() throws Exception;
}
