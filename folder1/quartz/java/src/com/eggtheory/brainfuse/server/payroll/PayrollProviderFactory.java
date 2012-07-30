package com.eggtheory.brainfuse.server.payroll;

import com.actbig.utils.WrappedException;
import com.eggtheory.brainfuse.bizobjects.user.PayrollTransaction;
import com.eggtheory.brainfuse.bizobjects.user.TutorPayroll;
import com.eggtheory.brainfuse.server.payroll.paypal.PaypalPayrollProvider;
import com.paypal.sdk.exceptions.PayPalException;

public class PayrollProviderFactory {
	
	public static PayrollProvider getPayrollProvider(int payrollOption, PayrollTransaction pt) throws WrappedException {
		if ( payrollOption == TutorPayroll.TUTOR_PAYROLL_PAYPAL ) {
			return new PaypalPayrollProvider(pt);
		} else if ( payrollOption == TutorPayroll.TUTOR_PAYROLL_DIRECT_DEPOIST ) {
			return new AACHPayrollProvider(pt);
		} else {
			throw new WrappedException();
		}
	}
}
