export type AdminLayout = {
  phone: boolean;
  horizontalSectionNavigation: boolean;
  modalScrollable: boolean;
  contentPadding: "compact" | "regular";
};

export function adminLayoutForWidth(width: number): AdminLayout {
  const phone = width < 600;
  return {
    phone,
    horizontalSectionNavigation: true,
    modalScrollable: true,
    contentPadding: phone ? "compact" : "regular",
  };
}
