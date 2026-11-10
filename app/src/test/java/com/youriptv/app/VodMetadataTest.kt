package com.youriptv.app

import com.youriptv.app.data.Repository
import com.youriptv.app.data.VodMetadata
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VodMetadataTest {
    @Test fun providerEpisodePicturesAndSeriesMetadataAreRetainedInPlaybackOrder() {
        val details = Repository.parseSeriesDetails(JSONObject("""{
            "info":{"cover":"https://images.example/show.jpg","plot":"The show", "backdrop_path":["",null,"https://images.example/wide.jpg"]},
            "episodes":{"2":[{"id":21,"episode_num":1,"title":"Second season","info":{"movie_image":"https://images.example/21.jpg"}}],
              "1":[{"id":"12","episode_num":"2","title":"Two","container_extension":"mkv","info":{"plot":"Episode plot","movie_image":"https://images.example/12.jpg"}},
                   {"id":"11","episode_num":1,"title":"One"}]}}
        """)) { id, extension -> "episode/$id.$extension" }
        assertEquals(listOf("11", "12", "21"), details.episodes.map { it.id })
        assertEquals("https://images.example/12.jpg", details.episodes[1].artwork)
        assertEquals("Episode plot", details.episodes[1].plot)
        assertEquals("episode/12.mkv", details.episodes[1].url)
        assertEquals("The show", details.plot)
        assertEquals("https://images.example/show.jpg", details.cover)
        assertEquals("https://images.example/wide.jpg", details.backdrop)
        assertEquals("", details.episodes.first().artwork) // No manufactured or silently repeated episode picture.
    }

    @Test fun missingAndMalformedOptionalFieldsNeverBecomeLiteralNullOrJsonText() {
        val details = VodMetadata.series(JSONObject("""{
            "info":{"cover":null,"plot":{"wrong":"type"},"backdrop_path":false},
            "episodes":{"1":[{"id":1,"title":null,"info":{"plot":null,"movie_image":null}},
                {"id":2,"season":"oops","episode_num":{},"info":{"movie_image":{"bad":"type"}}},
                null, {"id":null}, {"id":{}}, {"id":"../bad"}]}}
        """)) { id, _ -> "episode/$id" }
        assertEquals(2, details.episodes.size)
        assertEquals("", details.cover)
        assertEquals("", details.plot)
        assertTrue(details.episodes.all { it.artwork.isBlank() && it.plot.isBlank() })
        assertEquals(1, details.episodes[1].season)
        assertEquals(2, details.episodes[1].episode)
        assertEquals("", details.episodes[0].title)
    }

    @Test fun flatEpisodeArraysAndNumericIdsAreSupported() {
        val details = VodMetadata.series(JSONObject("""{"episodes":[{"id":3,"season":2,"episode_num":1,"info":{"episode_image":"https://images.example/3.png"}}]}""")) { id, _ -> id }
        assertEquals(2, details.episodes.single().season)
        assertEquals("3", details.episodes.single().url)
        assertEquals("https://images.example/3.png", details.episodes.single().artwork)
    }

    @Test fun existingEpisodesOnlyParserStillMatchesTheRicherParser() {
        val input = JSONObject("""{"episodes":{"1":[{"id":"1","episode_num":"1"}]}}""")
        assertEquals(Repository.parseSeriesDetails(input) { id, _ -> id }.episodes,
            Repository.parseSeriesEpisodes(input) { id, _ -> id })
    }

    @Test fun blankStreamIconUsesCoverButBadSchemesAreNotFetched() {
        assertEquals("https://images.example/poster.jpg", VodMetadata.catalogueArtwork("  ", "https://images.example/poster.jpg"))
        assertEquals("https://images.example/icon.jpg", VodMetadata.catalogueArtwork("https://images.example/icon.jpg", "https://images.example/poster.jpg"))
        assertEquals("", VodMetadata.catalogueArtwork("file:///etc/example", "javascript:example"))
        assertEquals("", VodMetadata.artwork("http://user:password@images.example/art.jpg"))
        assertEquals("", VodMetadata.artwork("https://images.example/art\n.jpg"))
        assertEquals("", VodMetadata.artwork("null"))
        assertEquals("https://images.example/art.png", VodMetadata.artwork(JSONArray("""[null,3,{},"https://images.example/art.png"]""")))
    }

    @Test fun incompleteMovieMetadataDoesNotAlterTheOriginalPlaybackItem() {
        val details = VodMetadata.movie(JSONObject("""{
            "info":{"description":"Film description", "movie_image":"", "cover_big":"https://images.example/film.jpg",
                "backdrop_path":["https://images.example/film-wide.jpg"], "releasedate":"2026-11-09",
                "genre":"Adventure", "duration":"01:23:45", "rating":7.5},
            "movie_data":{"stream_id":999,"direct_source":"https://not-used.example/video"}}
        """))
        assertEquals("Film description", details.plot)
        assertEquals("https://images.example/film.jpg", details.cover)
        assertEquals("https://images.example/film-wide.jpg", details.backdrop)
        assertEquals("2026-11-09", details.year)
        assertEquals("Adventure", details.genre)
        assertEquals("01:23:45", details.duration)
        assertEquals("7.5", details.rating)
        assertEquals("", VodMetadata.movie(JSONObject()).plot)
    }

    @Test fun badMovieFieldsFallBackToValidCatalogueCover() {
        val details = VodMetadata.movie(JSONObject("""{"info":{"movie_image":{},"plot":false,"rating":null,"genre":[]},
            "movie_data":{"stream_icon":"https://images.example/catalogue.jpg"}}"""))
        assertEquals("https://images.example/catalogue.jpg", details.cover)
        assertEquals("", details.plot)
        assertEquals("", details.rating)
        assertEquals("", details.genre)
    }

    @Test fun optionalTextAndArtworkAreBounded() {
        val details = VodMetadata.movie(JSONObject().put("info", JSONObject().put("plot", "a".repeat(20_000))))
        assertEquals(12_000, details.plot.length)
        assertEquals("", VodMetadata.artwork("https://images.example/" + "a".repeat(5000)))
    }
}
