/**
 * @license
 * Copyright (c) %FIRST_YEAR% %CURRENT_YEAR%, Oracle and/or its affiliates.
 * The Universal Permissive License (UPL), Version 1.0
 * as shown at https://oss.oracle.com/licenses/upl/
 * @ignore
 */
define(["require", "exports", "../tag-cloud-item/tag-cloud-item"], function (require, exports, tag_cloud_item_1) {
    "use strict";
    Object.defineProperty(exports, "__esModule", { value: true });
    exports.transformItem = transformItem;
    exports.executeLink = executeLink;
    /**
     * Transforms the corepack tagcloud item to preact tagcloud item.
     * @param item
     * @param index
     * @returns
     */
    function transformItem(dataItem) {
        const item = { ...tag_cloud_item_1.TagCloudItemDefaults, ...dataItem };
        return {
            color: item.color,
            accessibleLabel: item.shortDesc,
            value: item.value,
            label: item.label,
            id: item.key != null ? item?.key : item.id,
            role: (item.url ? 'link' : undefined)
        };
    }
    /**
     * Validates link URL protocol before browser navigation.
     * Copied from DomUtils.validateURL, but simplified because oj-c-tag-cloud only needs the default
     * http/https protocol allowlist and does not need legacy IE relative URL handling.
     * @param href URL to validate.
     * @throws Error if the URL uses an invalid protocol.
     */
    function validateURL(href) {
        const allowed = ['http:', 'https:'];
        const link = document.createElement('a');
        link.href = href;
        const protocol = link.protocol.toLowerCase();
        if (allowed.indexOf(protocol) < 0) {
            throw new Error(protocol + ' is not a valid URL protocol');
        }
    }
    /**
     * Get a pseudo link callback that loads a document into the existing or a new window.
     * @param {string} dest a URL to be loaded for the link
     */
    function executeLink(dest) {
        validateURL(dest);
        const newWindow = window.open(dest, '_blank');
        if (newWindow)
            newWindow.focus();
    }
});
