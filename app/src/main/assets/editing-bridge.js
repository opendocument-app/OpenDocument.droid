// Injected by PageView into every page the core serves, after the page's own scripts. It wires
// the page's editing callbacks to the app's bridge, and holds the one thing the app cannot see from
// outside: whether text is selected when a marking tool is pressed.
//
// The half of OpenDocument.website's frame-bridge.js that an app needs, over addJavascriptInterface
// rather than postMessage.
(function () {
  "use strict";

  var odr = window.odr;
  var bridge = window.paragraphListener;

  // a page with no scripts of the core's, a page this was injected into already, or a page the
  // bridge is not attached to
  if (!odr || !bridge || odr.androidEditing) {
    return;
  }

  var annotation = odr.annotation || null;

  // the page's callbacks, forwarded: the page owns what they mean, the app what they say
  odr.onEditChange = function (event) {
    bridge.editChanged(!!event.dirty, !!event.canUndo, !!event.canRedo);
  };
  odr.onEditRefused = function (event) {
    bridge.editRefused(String(event.reason || ""));
  };
  odr.onSelectionChange = function (style) {
    bridge.selectionChanged(JSON.stringify(style || {}));
  };
  odr.onCellsStale = function (detail) {
    bridge.cellsStale(detail && detail.cells ? detail.cells.length : 0);
  };

  // the annotator has no callback of its own, so the count of pending marks is reported after
  // every gesture that can change it. A mark taken from a selection settles 50ms after the pointer
  // lifts; this waits a little longer
  var reportedMarks = -1;

  function reportMarks() {
    var count = annotation.list().length;
    if (count === reportedMarks) {
      return;
    }
    reportedMarks = count;
    bridge.marksChanged(count);
  }

  function reportMarksSoon() {
    window.setTimeout(reportMarks, 120);
  }

  if (annotation) {
    // an armed tool marks a selection as it is made, which is what a touch screen needs: with a
    // selection standing, the selection's own toolbar is over the page
    annotation.setOptions({ markOnSelection: true });
    document.addEventListener("pointerup", reportMarksSoon);
    document.addEventListener("pointercancel", reportMarksSoon);
    document.addEventListener("selectionchange", reportMarksSoon);
  }

  function hasSelection() {
    var selection = window.getSelection();
    return !!selection && !selection.isCollapsed && selection.toString().length > 0;
  }

  /// Marks the selection once with @p tool, and leaves no tool armed.
  function markOnce(tool) {
    annotation.setTool(tool);
    annotation.mark();
    // disarmed before the selection is cleared, so the clear cannot mark it a second time
    annotation.setTool(null);
    var selection = window.getSelection();
    if (selection) {
      selection.removeAllRanges();
    }
    reportMarks();
  }

  odr.androidEditing = {
    /// A tool button was pressed. With text selected, the tool marks that selection once. Without
    /// one, the press arms the tool, and a second press disarms it. @p rgb is 0..1 per component.
    /// Answers the tool left armed, or null.
    tool: function (tool, rgb, width) {
      if (!annotation) {
        return null;
      }
      annotation.setColor(rgb);
      annotation.setWidth(width);
      if (tool !== "ink" && hasSelection()) {
        markOnce(tool);
      } else if (annotation.getTool() === tool) {
        annotation.setTool(null);
      } else {
        annotation.setTool(tool);
      }
      return annotation.getTool();
    },

    /// A new colour for @p tool: marks a selection once, recolours the tool if it is armed.
    recolor: function (tool, rgb, width) {
      if (!annotation) {
        return null;
      }
      if (tool !== "ink" && hasSelection()) {
        annotation.setColor(rgb);
        annotation.setWidth(width);
        markOnce(tool);
      } else if (annotation.getTool() === tool) {
        annotation.setColor(rgb);
      }
      return annotation.getTool();
    },

    disarm: function () {
      if (annotation) {
        annotation.setTool(null);
      }
    },

    undoMark: function () {
      if (annotation) {
        annotation.undo();
        reportMarks();
      }
    },
  };
})();
