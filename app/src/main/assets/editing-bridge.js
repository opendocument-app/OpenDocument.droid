// Injected by PageView into every page the core serves, after the page's own scripts. It points
// the page's editing callbacks at the app's bridge; what they mean is the page's.
(function () {
  "use strict";

  var odr = window.odr;
  var bridge = window.paragraphListener;

  // a page with no scripts of the core's, or a page the bridge is not attached to
  if (!odr || !bridge) {
    return;
  }

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
  odr.onAnnotationChange = function (event) {
    bridge.marksChanged(event.count);
  };

  if (odr.annotation) {
    // an armed tool marks a selection as it is made, which is what a touch screen needs: with a
    // selection standing, the selection's own toolbar is over the page
    odr.annotation.setOptions({ markOnSelection: true });
  }
})();
