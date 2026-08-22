package com.codpast.player.ui.mvi

interface MviState
interface MviIntent
interface MviEffect
/** Immutable snapshot of the UI at a given moment in time */
interface UiState

/** Actions triggered by the user or the system */
interface UserIntent

/** One-off events that shouldn't be preserved in state (Navigation, Toasts, Snackbars) */
interface UiEffect