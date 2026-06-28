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
}
