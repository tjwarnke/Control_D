package com.example.controld

class LikesActivity : AbstractListActivity() {
    override fun getTitleString(): String {
        return getString(R.string.all_likes)
    }

    override fun loadData() {
        // TODO: Load likes from database
        // For now, using sample data
        val sampleLikes = listOf(
            ActivityItem(
                "The Legend of Zelda: Breath of the Wild",
                "Liked Review",
                "March 15, 2024",
                "https://example.com/botw.jpg"
            ),
            ActivityItem(
                "Red Dead Redemption 2",
                "Liked Review",
                "March 10, 2024",
                "https://example.com/rdr2.jpg"
            ),
            ActivityItem(
                "God of War",
                "Liked Review",
                "March 5, 2024",
                "https://example.com/gow.jpg"
            )
        )
        
        // Sort the data based on the current sort type
        val sortedLikes = when (currentSortType) {
            SortType.NEWEST -> sampleLikes.sortedByDescending { it.date }
            SortType.OLDEST -> sampleLikes.sortedBy { it.date }
            SortType.NAME_ASC -> sampleLikes.sortedBy { it.gameTitle }
            SortType.NAME_DESC -> sampleLikes.sortedByDescending { it.gameTitle }
            else -> sampleLikes
        }
        
        adapter.submitList(sortedLikes)
    }
}