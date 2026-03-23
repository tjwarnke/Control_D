package com.example.controld

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.firebase.Firebase
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random


class HomeFragment : Fragment(), GameAdapter.Callbacks {
    private val firestore = Firebase.firestore
    private lateinit var popularGamesList: RecyclerView
    private lateinit var popularGamesAdapter: GameAdapter
    private lateinit var awardGamesList: RecyclerView
    private lateinit var awardGamesAdapter: GameAdapter
    private lateinit var highestGamesList: RecyclerView
    private lateinit var highestGamesAdapter: GameAdapter

    //Hold all games
    private lateinit var masterGames: MutableList<Game>
    //List length containing randomly selected games
    private val popLength = 5
    private lateinit var asyncFun: Job
    private lateinit var popGames: MutableList<Game>
    private lateinit var awardGames: MutableList<Game>
    private lateinit var highestGames: MutableList<Game>

    override fun onAttach(context: Context) {
        super.onAttach(context)
        //Putting it here for maybe very slightly faster time?
        //At the very least, pressing back button after clicking on game will
        //not require load time for games to repopulate
        asyncFun = CoroutineScope(Main).launch {
            populateMasterGames()
            popGames = populatePopGameAdapter()
            awardGames = populateAwardGameAdapter()
            highestGames = populateHighestGameAdapter()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        popularGamesAdapter = GameAdapter(context, this)
        awardGamesAdapter = GameAdapter(context, this)
        highestGamesAdapter = GameAdapter(context, this)

        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        popularGamesList = view.findViewById(R.id.popularGamesRecycle)
        awardGamesList = view.findViewById(R.id.awardWinningRecycle)
        highestGamesList = view.findViewById(R.id.highestRatedRecycle)
        
        // Setup lists button if it exists
        view.findViewById<Button>(R.id.lists_button)?.setOnClickListener {
            loadFragment(ListsFragment.newInstance(showPublicLists = true))
        }
        
        // Setup reviews button to show all reviews
        view.findViewById<Button>(R.id.reviews_button)?.setOnClickListener {
            startActivity(ReviewsActivity.createIntent(requireContext(), false))
        }
        
        setupRecyclerViews()
    }

    private fun setupRecyclerViews() {
        popularGamesList.layoutManager = LinearLayoutManager(context,LinearLayoutManager.HORIZONTAL,false)
        popularGamesList.adapter = popularGamesAdapter
        awardGamesList.layoutManager = LinearLayoutManager(context,LinearLayoutManager.HORIZONTAL,false)
        awardGamesList.adapter = awardGamesAdapter
        highestGamesList.layoutManager = LinearLayoutManager(context,LinearLayoutManager.HORIZONTAL,false)
        highestGamesList.adapter = highestGamesAdapter


        CoroutineScope(Main).launch{
            asyncFun.join()
            popularGamesAdapter.submitList(popGames)
            awardGamesAdapter.submitList(awardGames)
            highestGamesAdapter.submitList(highestGames)
        }
    }

    private fun populatePopGameAdapter(): MutableList<Game>{
        return gatherNGames(popLength)
    }

    private suspend fun populateAwardGameAdapter(): MutableList<Game>{
        return CoroutineScope(IO).async{
            val gameList = mutableListOf<Game>()
            masterGames.forEach { game ->
                firestore.collection("games").document(game.id).get()
                    .addOnSuccessListener { data ->
                        if(data.get("awardWinning") as Boolean? == true){
                            gameList.add(game)
                        }
                    }
            }
            return@async gameList
        }.await()
    }

    private suspend fun populateHighestGameAdapter(): MutableList<Game>{
        //Get tuple of game and average rating
        val gameRatings: MutableList<Pair<Game, Float>> = CoroutineScope(IO).async{
            val ratingsContainer = mutableListOf<Pair<Game, Float>>()
            masterGames.forEach { game ->
                val reviews = firestore.collection("games").document(game.id)
                    .collection("reviews").get().await()
                var ratingSum = 0.0.toFloat()
                var reviewCount = 0
                reviews.forEach { review ->
                    ratingSum += (review.get("rating") as Double).toFloat()
                    reviewCount++
                }
                //Get avg
                if (reviewCount != 0) {
                    ratingSum = ratingSum / reviewCount
                }
                ratingsContainer.add(Pair(game, ratingSum))
            }
            ratingsContainer.sortWith(compareByDescending{it.second})
            return@async ratingsContainer
        }.await()
        //Add sorted games to new list
        val gamesList = mutableListOf<Game>()
        gameRatings.forEach { pair ->
            gamesList.add(pair.first)
        }
        return gamesList
    }

    //For randomly selecting games
    private suspend fun populateMasterGames(){
        masterGames = CoroutineScope(IO).async {
            val gamesList = mutableListOf<Game>()
            try{
                var data = firestore.collection("games").get().await()
                for(game in data){
                    gamesList.add(Game(game.id,game.get("image") as String))
                }
            } catch(e: Exception){
                Log.d("Game", "Error populating game: ${e}")
            }
            return@async gamesList
        }.await()
    }

    private fun gatherNGames(n: Int): MutableList<Game>{
        val subsetGames = mutableListOf<Game>()
        val masterGamesSize = masterGames.count()
        while(subsetGames.count() != n){
            val game = masterGames[Random.nextInt(0,masterGamesSize)]
            if(game !in subsetGames){
                subsetGames.add(game)
            }
        }
        return subsetGames
    }

    private fun loadFragment(fragment: Fragment) {
        parentFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container, fragment)
            addToBackStack(null)
        }
    }

    override fun handleGamePress(id: String) {
        loadFragment(GameReviewFragment.newInstance(id))
    }

}
