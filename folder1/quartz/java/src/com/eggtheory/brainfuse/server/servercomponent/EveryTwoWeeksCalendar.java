package com.eggtheory.brainfuse.server.servercomponent;

import org.quartz.impl.calendar.BaseCalendar;
import java.util.Calendar;
import com.eggtheory.brainfuse.utils.DateUtils;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: Brainfuse Inc.</p>
 * @author Samuel L. Gabriel
 * @version 1.0
 */

public class EveryTwoWeeksCalendar extends BaseCalendar
{
    DateFormat df               = new SimpleDateFormat("MM/dd/yyyy");
    private Date startDate      = null;
    /**
    *
    * @param aStartDate - the new value for startDate
    */
    public void setStartDate(String aStartDate)throws ParseException{


        try
        {
//            System.out.println("setting start date to " + aStartDate);
            startDate = df.parse(aStartDate);
//            System.out.println("setting start date to " + startDate);

        }
        catch (ParseException ex)
        {
            ex.printStackTrace(System.out);
        }
    }

//    /**
//    *
//    * @param aStartDate - the new value for startDate
//    */
//    public void setStartDate(Date aStartDate){
//        startDate = aStartDate;
//    }



    public EveryTwoWeeksCalendar()
    {
        System.out.println("EveryTwoWeeksCalendar");
        try
        {
            startDate = df.parse("10/05/2004");
        }
        catch (ParseException ex)
        {
        }
    }

    public boolean isTimeIncluded(long time)
    {
        if (super.isTimeIncluded(time)== false ) return false;


        time      = super.buildHoliday(time);


        Calendar cal    = Calendar.getInstance();
        int diff        = DateUtils.dateDiff( Calendar.WEEK_OF_MONTH , startDate, new Date(time));
        boolean result  = false;
        if ( diff%2 == 0 ) result   = true;
        else result  = false;

        System.out.println("Checking " + startDate + " " + new Date(time) + " " + diff + " " + result);

        return result;

    }


}
