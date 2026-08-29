/* 冰读 iceReading - 阅读器 JS
 * 职责:
 *  1. 应用 CSS 变量
 *  2. 列分页(CSS multi-column)
 *  3. 滚动到指定页
 *  4. 选区高亮 JS bridge
 *  5. 调用 Android 端方法
 */

(function() {
    'use strict';

    var Android = window.NL = window.NL || {};

    // 1. 应用主题 + 字体 + 边距
    Android.applySettings = function(opts) {
        if (!opts) opts = {};
        var theme = opts.theme || 'theme-day';
        document.body.className = theme;

        var root = document.documentElement;
        root.style.setProperty('--font-size', (opts.fontSize || 18) + 'px');
        root.style.setProperty('--line-height', (opts.lineHeight || 1.5).toString());
        root.style.setProperty('--margin', (opts.margin || 16) + 'px');
        root.style.setProperty('--paragraph', (opts.paragraphSpacing || 8) + 'px');
        if (opts.fontFamily) {
            root.style.setProperty('--font-family', opts.fontFamily);
        }
        // 主题色
        var themeColors = {
            'theme-day':    {bg:'#FAFAFA', fg:'#1A1A1A', link:'#1565C0', highlight:'#FFEB3B'},
            'theme-paper':  {bg:'#F5EBD0', fg:'#3E2A14', link:'#1565C0', highlight:'#FFEB3B'},
            'theme-sepia':  {bg:'#F1E2C6', fg:'#5B4636', link:'#1565C0', highlight:'#FFEB3B'},
            'theme-night':  {bg:'#121212', fg:'#E0E0E0', link:'#64B5F6', highlight:'#FF9800'},
            'theme-dark':   {bg:'#0A0E1A', fg:'#B0B7C3', link:'#64B5F6', highlight:'#FF9800'}
        };
        var c = themeColors[theme] || themeColors['theme-day'];
        root.style.setProperty('--bg', c.bg);
        root.style.setProperty('--fg', c.fg);
        root.style.setProperty('--link', c.link);
        root.style.setProperty('--highlight', c.highlight);
    };

    // 2. 分页计算
    Android.getPageCount = function() {
        var content = document.getElementById('content');
        if (!content) return 1;
        // 简化:屏幕高度 / 行高 = 每屏行数;总行数 / 每屏行数 = 总页数
        var cs = window.getComputedStyle(content);
        var fs = parseFloat(cs.fontSize) || 18;
        var lh = parseFloat(cs.lineHeight) || 1.5;
        var lineHeightPx = fs * lh;
        var winHeight = window.innerHeight;
        var winWidth = window.innerWidth;
        var paddingTop = parseFloat(cs.paddingTop) || 0;
        var paddingBottom = parseFloat(cs.paddingBottom) || 0;
        var availHeight = winHeight - paddingTop - paddingBottom - 16; // 16 状态栏
        var linesPerPage = Math.max(1, Math.floor(availHeight / lineHeightPx));
        var totalText = content.innerText || content.textContent || '';
        var totalLines = Math.ceil(totalText.length / (winWidth / (fs * 0.5)));
        return Math.max(1, Math.ceil(totalLines / linesPerPage));
    };

    Android.getCurrentPage = function() {
        var sc = document.documentElement.scrollTop || document.body.scrollTop;
        var wh = window.innerHeight;
        return Math.floor(sc / wh) + 1;
    };

    Android.scrollToPage = function(page) {
        var wh = window.innerHeight;
        document.documentElement.scrollTop = (page - 1) * wh;
        document.body.scrollTop = (page - 1) * wh;
    };

    // 3. 获取当前选区
    Android.getSelection = function() {
        var sel = window.getSelection();
        if (!sel || sel.isCollapsed) return null;
        return {
            text: sel.toString(),
            start: sel.anchorOffset,
            end: sel.focusOffset
        };
    };

    // 4. 搜索(简单 substring 搜索)
    Android.search = function(query) {
        if (!query) return [];
        var body = document.body;
        var text = body.innerHTML;
        var results = [];
        var lc = text.toLowerCase();
        var qlc = query.toLowerCase();
        var idx = 0;
        while ((idx = lc.indexOf(qlc, idx)) >= 0) {
            results.push(idx);
            idx += qlc.length;
            if (results.length > 100) break;
        }
        return results;
    };

    Android.scrollToOffset = function(offset) {
        // 找最近的文本节点位置
        try {
            var range = document.createRange();
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
            var node, count = 0;
            while ((node = walker.nextNode())) {
                var len = node.nodeValue.length;
                if (count + len >= offset) {
                    range.setStart(node, offset - count);
                    range.collapse(true);
                    var rect = range.getBoundingClientRect();
                    window.scrollTo(0, window.scrollY + rect.top - 50);
                    return true;
                }
                count += len;
            }
        } catch (e) {}
        return false;
    };

    // 5. 获取当前章节所有文本(用于 CFI 估算)
    Android.getText = function() {
        return document.body.innerText || document.body.textContent || '';
    };

    // 6. 注入内容后初始化
    Android.loaded = function() {
        // 通知 Android 端
        if (window.NLAndroid) {
            try { window.NLAndroid.onLoaded(); } catch (e) {}
        }
    };

    Android.tocLoaded = function(toc) {
        if (window.NLAndroid) {
            try { window.NLAndroid.onTocLoaded(JSON.stringify(toc)); } catch (e) {}
        }
    };

    // 7. 高亮显示(供外部调用)
    Android.highlight = function(start, end, color) {
        try {
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
            var node, count = 0;
            while ((node = walker.nextNode())) {
                var len = node.nodeValue.length;
                if (count + len >= start) {
                    var range = document.createRange();
                    var s = Math.max(0, start - count);
                    var e = Math.min(len, end - count);
                    if (s < e) {
                        range.setStart(node, s);
                        range.setEnd(node, e);
                        var mark = document.createElement('mark');
                        if (color) mark.style.background = color;
                        try { range.surroundContents(mark); } catch (e) {}
                    }
                }
                count += len;
                if (count > end) break;
            }
        } catch (e) {}
    };

    Android.clearHighlights = function() {
        var marks = document.querySelectorAll('mark');
        for (var i = 0; i < marks.length; i++) {
            var parent = marks[i].parentNode;
            while (marks[i].firstChild) parent.insertBefore(marks[i].firstChild, marks[i]);
            parent.removeChild(marks[i]);
        }
    };

    // 8. 点击外部链接(交给 Android 处理)
    document.addEventListener('click', function(e) {
        var t = e.target;
        while (t && t.tagName !== 'A') t = t.parentNode;
        if (t && t.href && window.NLAndroid) {
            // 内链(#)不拦截
            if (t.href.indexOf('#') === -1) {
                e.preventDefault();
                try { window.NLAndroid.onLinkClick(t.href); } catch (e) {}
            }
        }
    });

    // 9. 长按选词(由 Android 端拦截)
    // 不做处理

    console.log('iceReading reader.js loaded');
})();
