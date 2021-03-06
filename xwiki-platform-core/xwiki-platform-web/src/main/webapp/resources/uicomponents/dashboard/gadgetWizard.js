require.config({
  paths: {
    'xwiki-ckeditor': new XWiki.Document('EditSheet', 'CKEditor').getURL('jsx', 'r=1')
  }
});

define(['jquery', 'xwiki-ckeditor'], function($, ckeditorPromise) {
  /*!
  #set ($l10nKeys = [
    'dashboard.gadgetSelector.title',
    'dashboard.gadgetEditor.title',
    'dashboard.gadgetEditor.changeGadget.label',
    'dashboard.gadgetEditor.gadgetTitle.label',
    'dashboard.gadgetEditor.gadgetTitle.hint'
  ])
  #set ($l10n = {})
  #foreach ($key in $l10nKeys)
    #set ($discard = $l10n.put($key, $services.localization.render($key)))
  #end
  */

  var l10n = $jsontool.serialize($l10n);

  var gadgetTitleTemplate = $(
    '<li class="macro-parameter" data-id="$gadgetTitle">' +
      '<div class="macro-parameter-name"></div>' +
      '<div class="macro-parameter-description"></div>' +
      '<input type="text" class="macro-parameter-field" name="$gadgetTitle"/>' + 
    '</li>'
  );
  gadgetTitleTemplate.find('.macro-parameter-name').text(l10n['dashboard.gadgetEditor.gadgetTitle.label']);
  gadgetTitleTemplate.find('.macro-parameter-description').text(l10n['dashboard.gadgetEditor.gadgetTitle.hint']);

  $('head').append($('<link type="text/css" rel="stylesheet"/>').attr('href',
    "$services.webjars.url('org.xwiki.contrib:application-ckeditor-webjar', 'plugins/xwiki-macro/macroWizard.min.css', {'evaluate': true})"));

  var getMacroCall = function(gadget, ckeditor) {
    if (gadget && typeof gadget.content === 'string') {
      return ckeditor.plugins.registered['xwiki-macro'].parseMacroCall(gadget.content);
    }
  };

  var getMacroWizard = function(ckeditor) {
    var deferred = $.Deferred();
    require(['macroWizard'], function(macroWizard) {
      deferred.resolve(ckeditor, macroWizard);
    });
    return deferred.promise();
  };

  var getDefaultGadgetTitle = function(macroEditor) {
    var gadgetName = macroEditor.attr('data-macroid').split('/')[0];
    return "${escapetool.d}services.localization.render('rendering.macro." + gadgetName + ".name')";
  };

  var currentGadget;

  // Customize the Macro Selector step when the Gadget Wizard is running.
  $(document).on('show.bs.modal', '.macro-selector-modal', function(event) {
    if (currentGadget && !$(this).hasClass('gadget-selector-modal')) {
      $(this).addClass('gadget-selector-modal');
      var modalTitleContainer = $(this).find('.modal-title');
      modalTitleContainer.prop('oldText', modalTitleContainer.text()).text(l10n['dashboard.gadgetSelector.title']);
    }
  });

  var restoreMacroSelector = function() {
    var macroSelectorModal = $('.gadget-selector-modal').removeClass('gadget-selector-modal');
    var modalTitleContainer = macroSelectorModal.find('.modal-title');
    modalTitleContainer.text(modalTitleContainer.prop('oldText'));
  };

  // Customize the Macro Editor step when the Gadget Wizard is running.
  var initialGadgetTitle;
  $(document).on('ready', '.macro-editor', function() {
    if (!currentGadget) {
      return;
    }
    var modal = $(this).closest('.modal');
    if (!modal.hasClass('gadget-editor-modal')) {
      initialGadgetTitle = currentGadget.title;
      // Customizations done everytime the Gadget Wizard is started.
      modal.addClass('gadget-editor-modal');
      var modalTitleContainer = modal.find('.modal-title');
      modalTitleContainer.prop('oldText', modalTitleContainer.text()).text(l10n['dashboard.gadgetEditor.title']);
      var changeMacroButton = modal.find('.modal-footer .btn-default').not('*[data-dismiss="modal"]');
      changeMacroButton.prop('oldText', changeMacroButton.text())
        .text(l10n['dashboard.gadgetEditor.changeGadget.label']);
    }
    // Customizations done everytime the Gadget Editor step is shown.
    var gadgetTitleContainer = gadgetTitleTemplate.clone();
    $(this).find('.macro-parameters').prepend(gadgetTitleContainer);
    var gadgetTitle = initialGadgetTitle || getDefaultGadgetTitle($(this));
    // Use the default gadget title next time (i.e. when the user changes the gadget).
    initialGadgetTitle = null;
    gadgetTitleContainer.find('input').val(gadgetTitle).focus();
  });

  var restoreMacroEditor = function() {
    var macroEditorModal = $('.gadget-editor-modal').removeClass('gadget-editor-modal');
    var modalTitleContainer = macroEditorModal.find('.modal-title');
    modalTitleContainer.text(modalTitleContainer.prop('oldText'));
    var changeMacroButton = macroEditorModal.find('.modal-footer .btn-default').not('*[data-dismiss="modal"]');
    changeMacroButton.text(changeMacroButton.prop('oldText'));
  };

  var runGadgetWizard = function(gadget, ckeditor, macroWizard) {
    currentGadget = gadget || {};
    return macroWizard(getMacroCall(gadget, ckeditor)).then(function(macroCall) {
      return {
        title: $('.macro-editor input[name="$gadgetTitle"]').val(),
        content: ckeditor.plugins.registered['xwiki-macro'].serializeMacroCall(macroCall)
      };
    }).always(function() {
      currentGadget = null;
      restoreMacroSelector();
      restoreMacroEditor();
    });
  };

  return function(gadget) {
    return ckeditorPromise.then(getMacroWizard).then($.proxy(runGadgetWizard, null, gadget));
  };
});
