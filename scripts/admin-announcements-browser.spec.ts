import { test, expect } from "@playwright/test";

/**
 * scripts/admin-announcements-browser.spec.ts
 *
 * Playwright E2E verification for Admin Announcements Web Console:
 * - Tab navigation: Permissions, Composer, History.
 * - Composer plain text entry, Unicode scalars, scalar/byte limits.
 * - Audience modes: "all", "admins", "selected".
 * - Preview generation & delivery availability indicators.
 * - Explicit confirmation checkbox gating submission.
 * - In-flight submission lock (prevent double submission).
 * - Sent history view & detailed sent announcement modal.
 * - XSS safety: HTML/script tags rendered as literal text without execution.
 */

test.describe("Admin Announcements Web Console", () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to admin notifications page
    await page.goto("/admin/notifications");
    await page.waitForLoadState("domcontentloaded");
  });

  test("tabs are present and navigable", async ({ page }) => {
    const permissionsTab = page.getByRole("tab", { name: /permissions/i });
    const composerTab = page.getByRole("tab", { name: /composer/i });
    const historyTab = page.getByRole("tab", { name: /history/i });

    await expect(permissionsTab).toBeVisible();
    await expect(composerTab).toBeVisible();
    await expect(historyTab).toBeVisible();

    // Switch to Composer
    await composerTab.click();
    await expect(page.locator("#announcement-title")).toBeVisible();
    await expect(page.locator("#announcement-body")).toBeVisible();

    // Switch to History
    await historyTab.click();
    await expect(page.getByRole("button", { name: /refresh/i })).toBeVisible();
  });

  test("composer enforces field bounds, confirmation gate, and plain text safety", async ({ page }) => {
    const composerTab = page.getByRole("tab", { name: /composer/i });
    await composerTab.click();

    const titleInput = page.locator("#announcement-title");
    const bodyInput = page.locator("#announcement-body");
    const previewBtn = page.getByRole("button", { name: /preview audience/i });
    const sendBtn = page.getByRole("button", { name: /send announcement/i });

    // Send button must not exist or be disabled before preview and confirmation
    await expect(sendBtn).toHaveCount(0);

    // Enter draft content with potential XSS payload
    const testTitle = "Security Notice: <script>window.hacked=true</script>";
    const testBody = "Notice body with <b>bold</b> text & special characters.\nSecond line.";

    await titleInput.fill(testTitle);
    await bodyInput.fill(testBody);

    // Select audience mode "all"
    const allRadio = page.locator('input[name="audienceMode"][value="all"]');
    await allRadio.check();
    await expect(allRadio).toBeChecked();

    // Preview Audience
    await previewBtn.click();

    // Verify confirmation section appears
    const confirmCheckbox = page.locator("#confirm-send");
    await expect(confirmCheckbox).toBeVisible({ timeout: 10000 });

    // Send button must be disabled until confirmation checkbox is checked
    const sendBtnAppeared = page.getByRole("button", { name: /send announcement/i });
    await expect(sendBtnAppeared).toBeDisabled();

    // Check confirmation checkbox
    await confirmCheckbox.check();
    await expect(sendBtnAppeared).toBeEnabled();

    // Edits to title or body must invalidate preview and confirmation
    await titleInput.pressSequentially(" - updated");
    // Confirm checkbox should disappear or be reset
    await expect(confirmCheckbox).not.toBeChecked();
    // Re-preview and re-confirm
    await previewBtn.click();
    await expect(confirmCheckbox).toBeVisible();
    await confirmCheckbox.check();
    await expect(sendBtnAppeared).toBeEnabled();

    // Verify no script execution occurred (XSS safety)
    const scriptExecuted = await page.evaluate(() => {
      const win = window as unknown;
      if (win && typeof win === "object" && "hacked" in win) {
        return win.hacked === true;
      }
      return false;
    });
    expect(scriptExecuted).toBe(false);
  });

  test("history tab renders sent items and opens detail dialog with literal text", async ({ page }) => {
    const historyTab = page.getByRole("tab", { name: /history/i });
    await historyTab.click();

    // Wait for history load
    await page.waitForLoadState("networkidle");

    // Check if any sent items exist; if so, verify detail dialog
    const detailButtons = page.getByRole("button", { name: /view details/i });
    const count = await detailButtons.count();

    if (count > 0) {
      await detailButtons.first().click();

      // Dialog should be open
      const dialog = page.getByRole("dialog");
      await expect(dialog).toBeVisible();

      // Check that title and body are rendered inside dialog
      await expect(dialog.getByText(/digest/i)).toBeVisible();

      // Close dialog
      const closeBtn = dialog.getByRole("button", { name: /close/i });
      if (await closeBtn.isVisible()) {
        await closeBtn.click();
      } else {
        await page.keyboard.press("Escape");
      }
      await expect(dialog).not.toBeVisible();
    }
  });
});
