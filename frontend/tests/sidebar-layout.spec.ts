import { expect, test } from "@playwright/test";

test("sidebar tools and navigation do not overlap at desktop width", async ({ page }) => {
  await page.setViewportSize({ width: 538, height: 1400 });
  await page.goto("/lab/shell");

  const createButton = page.getByRole("button", { name: "Создать чат" });
  const joinButton = page.getByRole("button", { name: "Войти по коду" });
  const navTitle = page.getByRole("heading", { name: "Твои чаты" });
  const refreshButton = page.getByRole("button", { name: "↻" });

  await expect(createButton).toBeVisible();
  await expect(joinButton).toBeVisible();
  await expect(navTitle).toBeVisible();
  await expect(refreshButton).toBeVisible();

  const createBox = await createButton.boundingBox();
  const joinBox = await joinButton.boundingBox();
  const navBox = await navTitle.boundingBox();
  const refreshBox = await refreshButton.boundingBox();

  expect(createBox).not.toBeNull();
  expect(joinBox).not.toBeNull();
  expect(navBox).not.toBeNull();
  expect(refreshBox).not.toBeNull();

  const toolBottom = Math.max(
    createBox!.y + createBox!.height,
    joinBox!.y + joinBox!.height,
  );

  expect(navBox!.y).toBeGreaterThanOrEqual(toolBottom + 16);
  expect(refreshBox!.y).toBeGreaterThanOrEqual(navBox!.y - 2);
});
