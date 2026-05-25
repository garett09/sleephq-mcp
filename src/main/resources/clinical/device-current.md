# Current device configuration

## CPAP

- **Machine:** ResMed AirSense 11 AutoSet
- **Mode:** Fixed CPAP, 10.6 cmH2O
- **EPR:** Off (do not enable — TECSA risk)
- **Mask:** Nasal pillows
- **SleepHQ machine ID:** see `SLEEPHQ_CPAP_MACHINE_ID` env var (default override available)

## O2 monitoring

- **Device:** O2 Ring (Viatom-class pulse oximeter)
- **SleepHQ machine ID:** see `SLEEPHQ_O2_MACHINE_ID` env var

## Share link

The default share-link token is in `SLEEPHQ_SHARE_LINK` env var if configured.

## Notes

Update this file when device or settings change. Resources auto-reload on the next request — no recompile needed.
