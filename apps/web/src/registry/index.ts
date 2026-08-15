// Jedyne miejsce w aplikacji, które zna komplet domen. Dodanie kolejnej to
// jeden import i jedna pozycja w tablicy — nic więcej.

import type { FeatureManifest } from '@/shared/featureManifest'
import { musicFeature } from '@/features/music'

export const features: FeatureManifest[] = [musicFeature]
