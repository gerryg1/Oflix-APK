package com.oflix.app.data

data class MediaItem(
    val id: String,
    val title: String,
    val posterUrl: String,
    val category: String, // "Film", "Series", "Komik", "Anichin"
    val year: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    val isNew: Boolean = false,
    val isTop: Boolean = false
)

object MockData {
    val trending = listOf(
        MediaItem("1", "Avatar: The Way of Water", "https://image.tmdb.org/t/p/w500/t6HIqrHezINNdIEueyD9PcRV4e.jpg", "Film", "2022", "7.6", "192m", true, true),
        MediaItem("2", "Squid Game", "https://image.tmdb.org/t/p/w500/dDlEmu3EZ0Pgg93K2SVNlcjCSv1.jpg", "Series", "2021", "8.0", "1 Season", false, true),
        MediaItem("3", "The Last of Us", "https://image.tmdb.org/t/p/w500/u3bZgnGQ9T01sWNhyveQz0wH0Hl.jpg", "Series", "2023", "8.7", "1 Season", true, true),
        MediaItem("4", "Spider-Man: No Way Home", "https://image.tmdb.org/t/p/w500/1g0dhYtq4irTY1R80vFAw4JQt00.jpg", "Film", "2021", "8.0", "148m")
    )
    
    val indonesianMovies = listOf(
        MediaItem("5", "The Big 4", "https://image.tmdb.org/t/p/w500/jrw68DnnQbKYJHQlsFjJq71XbsQ.jpg", "Film", "2022", "6.9", "141m"),
        MediaItem("6", "Pengabdi Setan 2: Communion", "https://image.tmdb.org/t/p/w500/wD8A41k9Q9mCifx4o9Uj4QcEwT8.jpg", "Film", "2022", "6.7", "119m"),
        MediaItem("7", "Stealing Raden Saleh", "https://image.tmdb.org/t/p/w500/A5mE6E3qjOckI2o86WbHkUuYVbA.jpg", "Film", "2022", "7.5", "154m")
    )
    
    val anime = listOf(
        MediaItem("8", "Attack on Titan", "https://image.tmdb.org/t/p/w500/8tZYtuWezp8JbcsvHYO0O46tFbo.jpg", "Anichin", "2013", "8.7", "4 Seasons", false, true),
        MediaItem("9", "Jujutsu Kaisen", "https://image.tmdb.org/t/p/w500/hFWP5HkbVEe40hrptcgHQbSpKT.jpg", "Anichin", "2020", "8.6", "2 Seasons", true),
        MediaItem("10", "Demon Slayer", "https://image.tmdb.org/t/p/w500/xUfRZu2mi8jH6SzQEJUV6wyxWn1.jpg", "Anichin", "2019", "8.7", "3 Seasons")
    )
    
    val komik = listOf(
        MediaItem("11", "Solo Leveling", "https://upload.wikimedia.org/wikipedia/en/thumb/8/87/Solo_Leveling_Webtoon_cover.png/220px-Solo_Leveling_Webtoon_cover.png", "Komik", isTop = true),
        MediaItem("12", "One Piece", "https://upload.wikimedia.org/wikipedia/en/9/90/One_Piece%2C_Volume_61_Cover_%28Japanese%29.jpg", "Komik", isTop = true)
    )

    // Dummy lists for remaining categories
    val indonesianDrama = listOf(
        MediaItem("13", "Gadis Kretek", "https://image.tmdb.org/t/p/w500/1Xy16iN6m6wEaB5J2ZpI4cZ8WvJ.jpg", "Series", "2023", "8.2", "1 Season")
    )
    val kdrama = listOf(
        MediaItem("14", "Crash Landing on You", "https://image.tmdb.org/t/p/w500/kZ1N1N5U2wX3E4bZ9fL7rV5r8yU.jpg", "Series", "2019", "8.7", "1 Season")
    )
    val westernTv = listOf(
        MediaItem("15", "Breaking Bad", "https://image.tmdb.org/t/p/w500/ggFHVNu6YYI5L9pCfOacjizRGt.jpg", "Series", "2008", "9.5", "5 Seasons")
    )
    val shortTv = listOf(
        MediaItem("16", "Love, Death & Robots", "https://image.tmdb.org/t/p/w500/cRiQCwZ7G7C3T50K8gD9PzVwH5.jpg", "Series", "2019", "8.4", "3 Seasons")
    )
    val horror = listOf(
        MediaItem("17", "The Conjuring", "https://image.tmdb.org/t/p/w500/wVYREutTvI2tmxr6ujrHT704wGF.jpg", "Film", "2013", "7.5", "112m")
    )
    val thailandDrama = listOf(
        MediaItem("18", "Girl From Nowhere", "https://image.tmdb.org/t/p/w500/8w0w5f1Z1mH7xQhB9x1Z9Z1Z1Z.jpg", "Series", "2018", "7.9", "2 Seasons")
    )
}
