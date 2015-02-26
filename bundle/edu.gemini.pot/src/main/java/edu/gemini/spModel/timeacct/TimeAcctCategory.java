//
// $
//

package edu.gemini.spModel.timeacct;

import java.util.Comparator;
import java.io.Serializable;

/**
 * A time accounting category instance.  The name is the code written into the
 * time accounting reports, which for normal countries is the two letter ISO
 * country code.  Also contains a display name. The categories are roughly the
 * same as the Phase 1 partner countries, but there isn't a 1:1 correspondence.
 */
public enum TimeAcctCategory implements Serializable {
        AR("Argentina"),
        AU("Australia"),
        BR("Brazil"),
        CA("Canada"),
        CL("Chile"),
        KR("Korea"),
        DD("Director's Time"),
        DS("Demo Science"),
        GS("Gemini Staff"),
        GT("Guaranteed Time"),
        JP("Subaru"),
        LP("Large Program"),
        SV("System Verification"),
        UH("University of Hawaii"),
        UK("United Kingdom"),
        US("United States"),
        XCHK("Keck Exchange"),
        ;

    /**
     * A comparator based on display name followed by code.
     */
    public static final Comparator<TimeAcctCategory> DISPLAY_NAME_COMPARATOR =
            new Comparator<TimeAcctCategory>() {
                public int compare(TimeAcctCategory c1, TimeAcctCategory c2) {
                    return c1.displayName.compareTo(c2.displayName);
                }
            };

    private String displayName;

    private TimeAcctCategory(String displayName) {
        if (displayName == null) throw new NullPointerException();
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
