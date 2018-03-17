package net.mbonnin.arcanetracker;

import android.graphics.Color;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;

import net.mbonnin.arcanetracker.adapter.Controller;
import net.mbonnin.hsmodel.Card;

import timber.log.Timber;

import static android.view.View.GONE;

/**
 * Created by martin on 10/14/16.
 */

public class DeckCompanion {
    private static final String KEY_LAST_USED_DECK_ID = "KEY_LAST_USED_DECK_ID";
    private Controller mController;

    View settings;
    TextView winLoss;
    public TextView deckName;

    private RecyclerView recyclerView;
    private ViewManager mViewManager;

    private ViewManager.Params mParams;
    private ViewManager.Params mRecyclerViewParams;
    private boolean isOpponent;
    private Deck mDeck;
    private ImageView background;

    public DeckCompanion(View v, boolean isOpponent) {
        mViewManager = ViewManager.get();
        this.isOpponent = isOpponent;

        Timber.d("screen: " + mViewManager.getWidth() + "x" + mViewManager.getHeight());

        int w = (int) (0.33 * 0.5 * mViewManager.getWidth());
        int h = mViewManager.getHeight();

        settings = v.findViewById(R.id.edit);
        winLoss = v.findViewById(R.id.winLoss);
        deckName = v.findViewById(R.id.deckName);
        background = v.findViewById(R.id.background);
        recyclerView = v.findViewById(R.id.recyclerView);

        int x = Settings.get("x" + isOpponent, -1);
        if (x == -1) {
            x = 0;
        }
        mParams = new ViewManager.Params();
        mParams.x = x;
        mParams.y = 0;
        mParams.w = w;
        mParams.h = h;

        mRecyclerViewParams = new ViewManager.Params();
        mRecyclerViewParams.w = w;
        mRecyclerViewParams.h = mViewManager.getHeight() - h;

        if (isOpponent) {
            settings.setVisibility(GONE);
            winLoss.setVisibility(GONE);


            recyclerView.setAdapter(Controller.Companion.get().getOpponentAdapter());
            setDeck(DeckList.getOpponentDeck());
        } else {
            new EditButtonCompanion(settings);
            String lastUsedId = PaperDb.INSTANCE.read(KEY_LAST_USED_DECK_ID);

            Deck deck = null;
            if (lastUsedId != null) {
                for (Deck deck2 : DeckList.get()) {
                    if (deck2.id.equals(lastUsedId)) {
                        deck = deck2;
                        break;
                    }
                }
                if (deck == null && lastUsedId.equals(DeckList.ARENA_DECK_ID)) {
                    deck = DeckList.getArenaDeck();
                }
            }

            if (deck == null) {
                deck = DeckList.createDeck(Card.CLASS_INDEX_WARRIOR);
                PaperDb.INSTANCE.write(KEY_LAST_USED_DECK_ID, deck.id);
            }

            recyclerView.setAdapter(Controller.Companion.get().getPlayerAdapter());
            setDeck(deck);
        }

        recyclerView.setBackgroundColor(Color.BLACK);
        recyclerView.setLayoutManager(new LinearLayoutManager(v.getContext()));
    }

    public void setDeck(Deck deck) {
        if (!isOpponent) {
            PaperDb.INSTANCE.write(KEY_LAST_USED_DECK_ID, deck.id);
            winLoss.setText(deck.wins + " - " + deck.losses);

            winLoss.setOnClickListener(v2 -> {
                View view2 = LayoutInflater.from(v2.getContext()).inflate(R.layout.edit_win_loss, null);

                NumberPicker win = view2.findViewById(R.id.win);
                win.setMinValue(0);
                win.setMaxValue(999);
                win.setValue(deck.wins);
                NumberPicker losses = view2.findViewById(R.id.loss);
                losses.setMinValue(0);
                losses.setMaxValue(999);
                losses.setValue(deck.losses);
                view2.findViewById(R.id.ok).setOnClickListener(v3 -> {
                    mViewManager.removeView(view2);
                    try {
                        deck.wins = win.getValue();
                    } catch (Exception e) {
                        deck.wins = 0;
                    }
                    try {
                        deck.losses = losses.getValue();
                    } catch (Exception e) {
                        deck.losses = 0;
                    }

                    DeckList.saveDeck(deck);

                    MainViewCompanion.Companion.getLegacyCompanion().setDeck(deck);
                });
                view2.findViewById(R.id.cancel).setOnClickListener(v3 -> {
                    mViewManager.removeView(view2);
                });

                mViewManager.addCenteredView(view2);
            });
            Controller.Companion.get().setPlayerDeck(deck.cards);
        }
        deck.checkClassIndex();

        mDeck = deck;
        background.setBackgroundDrawable(Utils.INSTANCE.getDrawableForClassIndex(deck.classIndex));
        deckName.setText(deck.name);
    }

    public Deck getDeck() {
        return mDeck;
    }
}

