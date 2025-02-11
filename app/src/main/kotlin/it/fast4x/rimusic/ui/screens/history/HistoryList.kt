package it.fast4x.rimusic.ui.screens.history

import androidx.annotation.OptIn
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.R
import it.fast4x.rimusic.enums.BuiltInPlaylist
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.SongSortBy
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.models.DateAgo
import it.fast4x.rimusic.models.EventWithSong
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.query
import it.fast4x.rimusic.service.isLocal
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.InHistoryMediaItemMenu
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenuLibrary
import it.fast4x.rimusic.ui.components.themed.NowPlayingShow
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.ui.items.SongItem
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.favoritesOverlay
import it.fast4x.rimusic.ui.styling.onOverlay
import it.fast4x.rimusic.ui.styling.overlay
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.BehindMotionSwipe
import it.fast4x.rimusic.utils.LeftAction
import it.fast4x.rimusic.utils.RightActions
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.downloadedStateMedia
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.getDownloadState
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.navigationBarPositionKey
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.songToggleLike
import it.fast4x.rimusic.utils.thumbnailRoundnessKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@kotlin.OptIn(ExperimentalTextApi::class)
@OptIn(UnstableApi::class)
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HistoryList(
    navController: NavController
) {
    val (colorPalette, typography) = LocalAppearance.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px

    val today = LocalDate.now()
    val thisMonday = today.with(DayOfWeek.MONDAY)
    val lastMonday = thisMonday.minusDays(7)

    val events = Database.events()
        .map { events ->
            events.groupBy {
                val date = //it.event.timestamp.toLocalDate()
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(it.event.timestamp),
                    TimeZone.getDefault().toZoneId()).toLocalDate()
                val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
                when {
                    daysAgo == 0 -> DateAgo.Today
                    daysAgo == 1 -> DateAgo.Yesterday
                    date >= thisMonday -> DateAgo.ThisWeek
                    date >= lastMonday -> DateAgo.LastWeek
                    else -> DateAgo.Other(date.withDayOfMonth(1))
                }
            }.toSortedMap(compareBy { dateAgo ->
                when (dateAgo) {
                    DateAgo.Today -> 0L
                    DateAgo.Yesterday -> 1L
                    DateAgo.ThisWeek -> 2L
                    DateAgo.LastWeek -> 3L
                    is DateAgo.Other -> ChronoUnit.DAYS.between(dateAgo.date, today)
                }
            })
        }
        .collectAsState(initial = emptyMap(), context = Dispatchers.IO)

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }
    var scrollToNowPlaying by remember {
        mutableStateOf(false)
    }

    var nowPlayingItem by remember {
        mutableStateOf(-1)
    }

    var listMediaItems = remember {
        mutableListOf<MediaItem>()
    }

    var selectItems by remember {
        mutableStateOf(false)
    }

    var thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )

    val navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.Bottom)

    Column (
        modifier = Modifier
            .background(colorPalette.background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
    ) {

        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxSize()
        ) {

            item(key = "header", contentType = 0) {
                HeaderWithIcon(
                    title = stringResource(R.string.history),
                    iconId = R.drawable.history,
                    enabled = false,
                    showIcon = false,
                    modifier = Modifier,
                    onClick = {}
                )
            }
            events.value.forEach { (dateAgo, events) ->
                stickyHeader {
                    Title(
                        title = when (dateAgo) {
                            DateAgo.Today -> stringResource(R.string.today)
                            DateAgo.Yesterday -> stringResource(R.string.yesterday)
                            DateAgo.ThisWeek -> stringResource(R.string.this_week)
                            DateAgo.LastWeek -> stringResource(R.string.last_week)
                            is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(
                                colorPalette.favoritesOverlay,
                                shape = thumbnailRoundness.shape()
                            )


                    )
                }

                items(
                    items = events.map {
                                       it.apply {
                                           this.event.timestamp = this.timestampDay!!
                                       }
                    }.distinctBy { it.song.id},
                    key = { it.event.id }
                ) { event ->

                    //BehindMotionSwipe(
                    //    content = {
                            val isLocal by remember { derivedStateOf { event.song.asMediaItem.isLocal } }
                            downloadState = getDownloadState(event.song.asMediaItem.mediaId)
                            val isDownloaded =
                                if (!isLocal) downloadedStateMedia(event.song.asMediaItem.mediaId) else true
                            val checkedState = rememberSaveable { mutableStateOf(false) }
                            SongItem(
                                song = event.song,
                                isDownloaded = isDownloaded,
                                onDownloadClick = {
                                    binder?.cache?.removeResource(event.song.asMediaItem.mediaId)
                                    query {
                                        Database.insert(
                                            Song(
                                                id = event.song.asMediaItem.mediaId,
                                                title = event.song.asMediaItem.mediaMetadata.title.toString(),
                                                artistsText = event.song.asMediaItem.mediaMetadata.artist.toString(),
                                                thumbnailUrl = event.song.thumbnailUrl,
                                                durationText = null
                                            )
                                        )
                                    }

                                    if (!isLocal)
                                        manageDownload(
                                            context = context,
                                            songId = event.song.asMediaItem.mediaId,
                                            songTitle = event.song.asMediaItem.mediaMetadata.title.toString(),
                                            downloadState = isDownloaded
                                        )
                                },
                                downloadState = downloadState,
                                thumbnailSizeDp = thumbnailSizeDp,
                                thumbnailSizePx = thumbnailSizePx,
                                onThumbnailContent = {
                                    if (nowPlayingItem > -1)
                                        NowPlayingShow(event.song.asMediaItem.mediaId)
                                },
                                trailingContent = {
                                    if (selectItems)
                                        Checkbox(
                                            checked = checkedState.value,
                                            onCheckedChange = {
                                                checkedState.value = it
                                                if (it) listMediaItems.add(event.song.asMediaItem) else
                                                    listMediaItems.remove(event.song.asMediaItem)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = colorPalette.accent,
                                                uncheckedColor = colorPalette.text
                                            ),
                                            modifier = Modifier
                                                .scale(0.7f)
                                        )
                                    else checkedState.value = false
                                },
                                modifier = Modifier
                                    .combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenuLibrary(
                                                    navController = navController,
                                                    mediaItem = event.song.asMediaItem,
                                                    onDismiss = menuState::hide
                                                )
                                            }
                                        },
                                        onClick = {
                                            binder?.player?.forcePlay(event.song.asMediaItem)
                                        }
                                    )
                                    .background(color = colorPalette.background0)
                                    .animateItemPlacement()
                            )
                        /*
                        },
                        leftActionsContent = {
                            LeftAction(
                                icon = R.drawable.play_skip_forward,
                                backgroundColor = Color.Transparent, //colorPalette.background4,
                                onClick = {
                                    binder?.player?.addNext( event.song.asMediaItem )
                                }
                            )
                        },
                        rightActionsContent = {

                            var likedAt by remember {
                                mutableStateOf<Long?>(null)
                            }
                            LaunchedEffect(Unit, event.song.asMediaItem.mediaId) {
                                Database.likedAt(event.song.asMediaItem.mediaId).collect { likedAt = it }
                            }

                            RightActions(
                                iconAction1 = if (likedAt == null) R.drawable.heart_outline else R.drawable.heart,
                                backgroundColorAction1 = Color.Transparent, //colorPalette.background4,
                                onClickAction1 = {
                                    songToggleLike(event.song)
                                },
                                iconAction2 = R.drawable.trash,
                                backgroundColorAction2 = Color.Transparent, //colorPalette.iconButtonPlayer,
                                enableAction2 = false,
                                onClickAction2 = {
                                    /*
                                    if (binder != null) {
                                        when (builtInPlaylist) {
                                            BuiltInPlaylist.Offline ->
                                                binder.cache.removeResource(song.asMediaItem.mediaId)
                                            BuiltInPlaylist.Favorites -> {}
                                            BuiltInPlaylist.Downloaded -> {/*
                                                binder.downloadCache.removeResource(song.asMediaItem.mediaId)
                                                manageDownload(
                                                    context = context,
                                                    songId = song.asMediaItem.mediaId,
                                                    songTitle = song.asMediaItem.mediaMetadata.title.toString(),
                                                    downloadState = false
                                                )*/
                                            }
                                            BuiltInPlaylist.Top -> {}
                                        }
                                    }
                                     */
                                }
                            )


                        },
                        onHorizontalSwipeWhenActionDisabled = {}
                    )
                    */
                }
            }
        }

    }
}

