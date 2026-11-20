# Run with: perl tests/settings_contract.t (no Android build needed).
use strict;
use warnings;
use FindBin;
use Test::More;
sub source {
    my ($name) = @_;
    open my $fh, '<', "$FindBin::Bin/../app/src/main/java/com/douban/pure/$name.java" or die $!;
    local $/; return <$fh>;
}
my $dialog = source('InAppSettingsDialog');
my $layout = source('DoubanLayoutPurifier');
my $prefs = source('PureSettings');
my $provider = source('SettingsProvider');
for my $case (
    ['搜索页屏蔽热门话题', 'SEARCH_HOT_TOPICS', 'true'],
    ['搜索页屏蔽实时热门榜', 'SEARCH_TRENDS', 'false'],
    ['侧边栏隐藏我的背包', 'SIDE_BAG', 'true'],
    ['侧边栏隐藏草稿箱', 'SIDE_DRAFTS', 'true'],
    ['侧边栏隐藏签到', 'SIDE_CHECKIN', 'true'],
    ['侧边栏隐藏帮助与反馈', 'SIDE_FEEDBACK', 'true'],
    ['侧边栏隐藏社区管理中心', 'SIDE_COMMUNITY', 'true'],
) {
    my ($title, $suffix, $default) = @$case;
    my @rows = $dialog =~ /addSwitchRow\([^;]*?"\Q$title\E",\s*"[^"]*",\s*PureSettings\.KEY_HIDE_(\w+),\s*(true|false)/g;
    is_deeply(\@rows, [$suffix, $default], "row for $suffix");
    if ($suffix =~ /^SIDE_/) {
        like($layout, qr/return PureSettings\.KEY_HIDE_${suffix};.*?boolean hideSidebarOption\(Context ctx, String key\).*?PureSettings\.getBoolean\(ctx, key,/s,
            "consumer for $suffix");
    } else {
        like($layout, qr/getBoolean\(ctx, PureSettings\.KEY_HIDE_${suffix}, $default\)/, "consumer for $suffix");
    }
}
like($dialog, qr/getInt\(activity, PureSettings\.KEY_UI_LAST_TAB, 0\).*?initialTab < 0 \|\| initialTab >= pageViews.size\(\).*?initialTab = 0;/s, 'bounded local tab restore');
like($dialog, qr/if \(remember\)\s*\{\s*PureSettings\.setInt\(context, PureSettings\.KEY_UI_LAST_TAB, index\)/, 'tab selection persisted');
like($dialog, qr/getBoolean\(activity, prefKey, defaultValue\).*?setOnCheckedChangeListener\(.*?setBoolean\(activity, prefKey, isChecked\)/s, 'switch reads and writes the same key');
like($provider, qr/if \(!sp.contains\(arg\)\) return res;[^}]*res.putBoolean\("value", sp.getBoolean\(arg, def\)\)/s, 'missing provider key does not mask fallback');
like($prefs, qr/"get_boolean".*?res.containsKey\("value"\).*?return res.getBoolean.*?if \(local.contains\(key\)\) return local.getBoolean.*?DoubanPureHook.remotePreferences\(\)/s, 'provider then local then legacy remote');
like($prefs, qr/public static final String KEY_REPLACE_SEARCH_BOARDS = "replace_search_boards";/,
    'replacement uses its own persisted key');
like($prefs, qr/public static final boolean DEFAULT_REPLACE_SEARCH_BOARDS = true;/,
    'replacement defaults on for existing users');
my ($search_page) = $dialog =~ m{// === TAB 3:[^\n]*\n(.*?)pageViews\.add\(page3\);}s;
my @replacement_rows = ($search_page // '') =~ /addSwitchRow\(card3_1, activity, "用电影榜和电视榜替换讨论榜",\s*"[^"]*",\s*PureSettings\.(KEY_\w+),\s*PureSettings\.(DEFAULT_\w+)/g;
is_deeply(\@replacement_rows, ['KEY_REPLACE_SEARCH_BOARDS', 'DEFAULT_REPLACE_SEARCH_BOARDS'],
    'search page has one independently persisted replacement row with the shared default');
done_testing();
