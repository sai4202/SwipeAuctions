/**
 * The 4 fields required for any used/repossessed vehicle listing (see
 * detailFields.ts's requiresVehicleDetails / AdminStockController.requireVehicleDetails) — shown
 * directly in list-view rows (AuctionListTable, EventsBrowse's per-event table) instead of only on
 * the detail page's tabs, matching how the reference site always surfaces Reg No/Chassis No/Yard
 * right on the row. Renders nothing if none of the 4 are present (e.g. a non-vehicle item, or a
 * NEW vehicle that's exempt) — this is a display convenience, not where the requirement is enforced.
 */
export default function VehicleDetailStrip({ attributes }: { attributes: Record<string, string> }) {
  const yardName = attributes.yardName?.trim()
  const yardLocation = attributes.yardLocation?.trim()
  const registrationNumber = attributes.registrationNumber?.trim()
  const chassisNo = attributes.chassisNo?.trim()

  if (!yardName && !yardLocation && !registrationNumber && !chassisNo) return null

  return (
    <div className="vehicle-detail-strip">
      {yardName && <div><span className="muted">Yard Name:</span> <b>{yardName}</b></div>}
      {yardLocation && <div><span className="muted">Yard Location:</span> <b>{yardLocation}</b></div>}
      {(registrationNumber || chassisNo) && (
        <div className="vehicle-detail-strip-row">
          {registrationNumber && <span><span className="muted">Reg No:</span> <b>{registrationNumber}</b></span>}
          {chassisNo && <span><span className="muted">Chassis No:</span> <b>{chassisNo}</b></span>}
        </div>
      )}
    </div>
  )
}
