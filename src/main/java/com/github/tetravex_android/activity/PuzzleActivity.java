/*
 * Copyright (C) 2014 John Hunt <john.alma.hunt@gmail.com>
 *
 * This file is part of Tetravex Android.
 *
 * Tetravex Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tetravex Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Tetravex Android.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.tetravex_android.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Chronometer;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.github.tetravex_android.Constants;
import com.github.tetravex_android.R;
import com.github.tetravex_android.TetravexApp;
import com.github.tetravex_android.data.HiScoreManager;
import com.github.tetravex_android.game.Puzzle;
import com.github.tetravex_android.game.Puzzle.PuzzleState;
import com.github.tetravex_android.game.Tile;
import com.github.tetravex_android.data.BoardAdapter;

/**
 * This activity is where the Tetravex gameplay takes place
 */
public class PuzzleActivity extends Activity
        implements View.OnTouchListener, View.OnDragListener {

    static final int PAUSE_REQUEST = 1;  // The request code

    private Puzzle mPuzzle;

    private GridView mTargetGridView;
    private GridView mSourceGridView;

    // fields to support drag 'n drop
    private Tile mDraggedTile = null;
    private int mDraggedTileStartingX = -1;
    private int mDraggedTilePosition = -1;

    // used for the puzzle timer
    private Chronometer mTimer;
    private long mPausedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen
        this.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_puzzle);
        TetravexApp.injectActivity(this);
        mTimer = (Chronometer) findViewById(R.id.timer);

        // get the size of the board from the saved setting
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String boardSize = settings.getString(getString(R.string.pref_size_key),
                Constants.DEFAULT_SIZE);
        // access the setting to decide whether or not to color the tiles
        boolean colorTiles = settings.getBoolean(getString(R.string.pref_color_key), true);

        mPuzzle = new Puzzle(Integer.valueOf(boardSize));
        mPuzzle.setColor(colorTiles);

        mTargetGridView = (GridView) findViewById(R.id.target_board);
        mSourceGridView = (GridView) findViewById(R.id.source_board);

        formatTargetBoard();
        formatSourceBoard();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mPuzzle.getState().equals(PuzzleState.PAUSED)) {
            mPuzzle.setState(PuzzleState.IN_PROGRESS);
            resumeTimer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mPuzzle.getState().equals(PuzzleState.IN_PROGRESS)) {
            mPuzzle.setState(PuzzleState.PAUSED);
            pauseTimer();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == PAUSE_REQUEST) {
            // handle result codes from the PauseActivity
            if (resultCode == PauseActivity.RESULT_NEW_GAME) {
                Intent intent = new Intent(this, PuzzleActivity.class);
                startActivity(intent);
                finish();
            } else if (resultCode == PauseActivity.RESULT_QUIT_GAME) {
                // Go back to the dashboard
                finish();
            }
        }
    }

    /**
     * Sets the adapter and number of columns for the target board GridView
     */
    private void formatTargetBoard() {
        mTargetGridView.setAdapter(new BoardAdapter(this, false, mPuzzle));
        mTargetGridView.setNumColumns(mPuzzle.getSize());
        mTargetGridView.setOnTouchListener(this);
    }

    /**
     * Set the adapter and number of columns for the source board GridView
     */
    private void formatSourceBoard() {
        mSourceGridView.setAdapter(new BoardAdapter(this, true, mPuzzle));
        mSourceGridView.setNumColumns(mPuzzle.getSize());
        mSourceGridView.setOnTouchListener(this);
    }

    /**
     * Handle events when the user touches the screen
     *
     * @param view        the View that was clicked
     * @param motionEvent the system event associated with the touch
     * @return {@code true} if the event is consumed, {@code false} otherwise
     */
    public boolean onTouch(View view, MotionEvent motionEvent) {

        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            GridView parent = (GridView) view;

            int x = (int) motionEvent.getX();
            int y = (int) motionEvent.getY();
            int position = parent.pointToPosition(x, y);
            int relativePosition = position - parent.getFirstVisiblePosition();
            final View target = parent.getChildAt(relativePosition);

            // drag only numbered tiles
            if (target != null &&
                    target.getTag().equals(Constants.TAG_NUMBERED_TILE)) {
                // start a drag event - on a separate thread to not interfere with the
                // touch event already in progress

                target.post(new Runnable() {
                    @Override
                    public void run() {
                        ClipData data = ClipData.newPlainText("DragData", "");
                        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(target);
                        target.startDrag(data, shadowBuilder, target, 0);
                        target.setVisibility(View.INVISIBLE);
                    }
                });

                mDraggedTilePosition = parent.getPositionForView(target);
                if (parent.equals(mSourceGridView)) {
                    mDraggedTileStartingX = mPuzzle.getSize();
                } else {
                    mDraggedTileStartingX = 0;
                }
                mDraggedTile = mPuzzle.getTileByPosition(mDraggedTileStartingX, mDraggedTilePosition);
                mPuzzle.setTile(mDraggedTileStartingX, mDraggedTilePosition, null);

                // draw an empty slot where the dragged tile just was
                ((BoardAdapter) parent.getAdapter()).notifyDataSetChanged();

                // start the timer the first time a tile is touched
                if (mPuzzle.getState().equals(PuzzleState.NEW)) {
                    mPuzzle.setState(PuzzleState.IN_PROGRESS);
                    mTimer.setBase(SystemClock.elapsedRealtime());
                    mTimer.start();
                }
            }
            // report that the event was consumed
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onDrag(View view, DragEvent event) {

        // Handles each of the expected events
        switch (event.getAction()) {

            //signal for the start of a drag and drop operation.
            case DragEvent.ACTION_DRAG_STARTED:
                break;

            //the drag point has entered the bounding box of the View
            case DragEvent.ACTION_DRAG_ENTERED:
                ((ImageView) view).setColorFilter(Color.YELLOW);
                break;

            //the user has moved the drag shadow outside the bounding box of the View
            case DragEvent.ACTION_DRAG_EXITED:
                ((ImageView) view).clearColorFilter();
                break;

            //drag shadow has been released,the drag point is within the bounding box of the View
            case DragEvent.ACTION_DROP:
                GridView targetGrid = (GridView) view.getParent();
                int index = targetGrid.indexOfChild(view);

                int droppedStartIndex;
                if (targetGrid.equals(mSourceGridView)) {
                    droppedStartIndex = mPuzzle.getSize();
                } else {
                    droppedStartIndex = 0;
                }
                mPuzzle.setTile(droppedStartIndex, index, mDraggedTile);
                ((BoardAdapter) targetGrid.getAdapter()).notifyDataSetChanged();

                // validate the board now the tile has been dropped
                if (mPuzzle.isSolved()) {
                    puzzleSolvedActions();
                }
                break;

            //the drag and drop operation has concluded.
            case DragEvent.ACTION_DRAG_ENDED:
                if (!event.getResult()) {
                    // tile dropped in an invalid area of the screen; put it back
                    mPuzzle.setTile(mDraggedTileStartingX, mDraggedTilePosition, mDraggedTile);
                    ((BoardAdapter) mTargetGridView.getAdapter()).notifyDataSetChanged();
                    ((BoardAdapter) mSourceGridView.getAdapter()).notifyDataSetChanged();
                }
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        // confirm quitting the game
        if (mPuzzle.getState().equals(PuzzleState.IN_PROGRESS)) {
            pauseTimer();
            DialogInterface.OnClickListener dialogClickListener =
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int selection) {
                            switch (selection) {
                                case DialogInterface.BUTTON_POSITIVE:
                                    finish();
                                    break;
                                case DialogInterface.BUTTON_NEGATIVE: // fall-through
                                default:
                                    resumeTimer();
                                    break;
                            }
                        }
                    };

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getResources().getString(R.string.dialog_quit_game_prompt));
            builder.setPositiveButton(getResources().getString(R.string.dialog_ok),
                    dialogClickListener);
            builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),
                    dialogClickListener);
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    resumeTimer();
                }
            });
            builder.show();
        } else {
            finish();
        }
    }

    /**
     * Perform necessary actions when puzzle is solved.
     */
    private void puzzleSolvedActions() {
        mTimer.stop();
        mPuzzle.setState(PuzzleState.COMPLETED);

        // set the tiles to not be draggable
        mTargetGridView.setOnTouchListener(null);

        showPuzzleSolvedToast();
        updateHighScores();
        hideStartingBoard();
        showButtonsOnCompleted();
    }

    /**
     * Show the buttons for new game and high scores
     */
    private void showButtonsOnCompleted() {
        LinearLayout buttons = (LinearLayout) findViewById(R.id.puzzle_buttons);
        Animation fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
        fadeIn.setDuration(Constants.BUTTONS_FADE_IN_MS);
        buttons.startAnimation(fadeIn);
        buttons.setVisibility(View.VISIBLE);
    }

    /**
     * Hide the arrow and the starting grid
     */
    private void hideStartingBoard() {
        ImageView arrowImg = (ImageView) findViewById(R.id.arrow);
        Animation out = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        arrowImg.startAnimation(out);
        arrowImg.setVisibility(View.INVISIBLE);
        GridView sourceGrid = (GridView) findViewById(R.id.source_board);
        sourceGrid.startAnimation(out);
        sourceGrid.setVisibility(View.INVISIBLE);
    }

    /**
     * Show a Toast notification that the puzzle has been solved
     */
    private void showPuzzleSolvedToast() {
        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(getLayoutInflater().inflate(R.layout.victory_toast, null));
        toast.show();
    }

    /**
     * Add a new score to the scores database if it meets the criteria for a high score.
     * Uses the time from the timer as a basis for the score.
     */
    private void updateHighScores() {
        HiScoreManager highScores = new HiScoreManager();
        highScores.setContext(this);

        TetravexApp.getApplication(this).inject(highScores);
        String timerValue = mTimer.getText().toString();
        highScores.addScore(mPuzzle.getSize(), timerValue);
    }

    /**
     * Perform the necessary actions when the pause button is clicked
     *
     * @param view the View that was clicked
     */
    public void pauseButtonClicked(View view) {
        if (mPuzzle.getState().equals(PuzzleState.IN_PROGRESS)) {
            pauseTimer();
        }
        Intent intent = new Intent(this, PauseActivity.class);
        startActivityForResult(intent, 1);
    }

    /**
     * Stop the timer and save its state to resume later
     */
    private void pauseTimer() {
        mTimer.stop();
        mPausedTime = SystemClock.elapsedRealtime() - mTimer.getBase();
        mPuzzle.setState(PuzzleState.PAUSED);
    }

    /**
     * Re-start the timer from where it was left
     */
    private void resumeTimer() {
        mPuzzle.setState(PuzzleState.IN_PROGRESS);
        mTimer.setBase(SystemClock.elapsedRealtime() - mPausedTime);
        mTimer.start();
    }

    /**
     * Start a new game at the user's request. Removes the current activity from the back stack.
     *
     * @param view the View that was clicked
     */
    public void puzzleNewGameButtonClicked(View view) {
        Intent intent = new Intent(this, PuzzleActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Show the high scores at the user's request
     *
     * @param view the View that was clicked
     */
    public void puzzleScoresButtonClicked(View view) {
        Intent intent = new Intent(this, HiScoreActivity.class);
        startActivity(intent);
    }
}