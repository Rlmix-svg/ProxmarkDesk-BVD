// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.session;

/**
 * State of the RF tag in the antenna field.
 * A command failure does NOT automatically set REMOVED.
 * Only an explicit search returning no tag sets REMOVED.
 */
public enum TagState {
    /** No search has been performed yet, or state is indeterminate. */
    UNKNOWN,
    /** A tag was detected and identified in the last search. */
    PRESENT,
    /** A tag was selected but stopped responding mid-operation. */
    NOT_RESPONDING,
    /** An explicit search found no tag (previous card info kept as history). */
    REMOVED
}
