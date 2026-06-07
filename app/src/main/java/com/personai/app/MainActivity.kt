package com.personai.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.personai.app.core.SoulSpark
import com.personai.app.databinding.ActivityMainBinding
import com.personai.app.soul.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val spark get() = PersonAIApplication.soulSpark

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        observe()
        binding.btnGenesis.setOnClickListener {
            val name      = binding.etName.text.toString().trim().ifEmpty { "unnamed" }
            val archetype = selectedArchetype()
            lifecycleScope.launch { spark.genesis(archetype, name) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        spark.emergencySave()
    }

    private fun observe() {
        lifecycleScope.launch {
            spark.soulState.collect { state ->
                when (state) {
                    is SoulEngine.SoulState.Uninitialized -> show(genesis = true)
                    is SoulEngine.SoulState.Loading       -> show(loading = true)
                    is SoulEngine.SoulState.Alive         -> showSoul(state)
                    is SoulEngine.SoulState.Error         -> {
                        show(loading = true)
                        binding.tvLoadingStatus.text = "Error: ${state.msg}"
                    }
                }
            }
        }
    }

    private fun show(genesis: Boolean = false, loading: Boolean = false, soul: Boolean = false) {
        binding.genesisPanel.visibility = if (genesis) View.VISIBLE else View.GONE
        binding.loadingPanel.visibility = if (loading) View.VISIBLE else View.GONE
        binding.soulPanel.visibility    = if (soul)    View.VISIBLE else View.GONE
    }

    private fun showSoul(state: SoulEngine.SoulState.Alive) {
        show(soul = true)
        val s = state.soul; val o = s.neural.ocean
        binding.tvEntityName.text     = s.identity.name.uppercase()
        binding.tvArchetype.text      = s.genesis.archetype.displayName
        binding.tvMood.text           = s.identity.currentMood.display
        binding.tvNarrative.text      = s.identity.selfNarrative
        binding.tvInteractions.text   = "${s.meta.totalInteractions} interactions"
        binding.tvEvolutionCycle.text = "Evolution cycle ${s.meta.evolutionCycle}"
        binding.progressO.progress = (o.openness          * 100).toInt()
        binding.progressC.progress = (o.conscientiousness * 100).toInt()
        binding.progressE.progress = (o.extraversion      * 100).toInt()
        binding.progressA.progress = (o.agreeableness     * 100).toInt()
        binding.progressN.progress = (o.neuroticism       * 100).toInt()
        s.identity.obsession?.let {
            binding.tvObsession.text       = "Currently thinking about: $it"
            binding.tvObsession.visibility = View.VISIBLE
        } ?: run { binding.tvObsession.visibility = View.GONE }
    }

    private fun selectedArchetype() = when (binding.rgArchetype.checkedRadioButtonId) {
        binding.rbGuardian.id -> Archetype.GUARDIAN
        binding.rbSpark.id    -> Archetype.SPARK
        binding.rbSage.id     -> Archetype.SAGE
        binding.rbShadow.id   -> Archetype.SHADOW
        binding.rbWanderer.id -> Archetype.WANDERER
        else                  -> Archetype.EXPLORER
    }
}
