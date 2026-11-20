package com.douban.pure;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class PureSettingsTest {
    @Test public void boardReplacementDefaultsOnAndOnlyWholeBoardHidingOverridesIt() {
        Context context = RuntimeEnvironment.getApplication();
        assertTrue(PureSettings.shouldReplaceSearchBoards(context));

        PureSettings.setBoolean(context, PureSettings.KEY_HIDE_SEARCH_TRENDS, true);
        assertFalse(PureSettings.shouldReplaceSearchBoards(context));
        assertTrue(PureSettings.getBoolean(context, PureSettings.KEY_REPLACE_SEARCH_BOARDS,
                PureSettings.DEFAULT_REPLACE_SEARCH_BOARDS));
        PureSettings.setBoolean(context, PureSettings.KEY_HIDE_SEARCH_TRENDS, false);
        assertTrue(PureSettings.shouldReplaceSearchBoards(context));

        for (boolean hideTrends : new boolean[]{false, true}) {
            for (boolean hideTopics : new boolean[]{false, true}) {
                PureSettings.setBoolean(context, PureSettings.KEY_HIDE_SEARCH_TRENDS, hideTrends);
                PureSettings.setBoolean(context, PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS, hideTopics);
                for (boolean replace : new boolean[]{false, true}) {
                    PureSettings.setBoolean(context, PureSettings.KEY_REPLACE_SEARCH_BOARDS, replace);
                    assertEquals(!hideTrends && replace, PureSettings.shouldReplaceSearchBoards(context));
                    assertEquals(replace, PureSettings.getBoolean(context, PureSettings.KEY_REPLACE_SEARCH_BOARDS, !replace));
                    assertEquals(hideTrends, PureSettings.getBoolean(context, PureSettings.KEY_HIDE_SEARCH_TRENDS, !hideTrends));
                    assertEquals(hideTopics, PureSettings.getBoolean(context, PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS, !hideTopics));
                }
            }
        }
    }
}
