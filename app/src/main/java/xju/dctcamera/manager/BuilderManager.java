package xju.dctcamera.manager;

import android.graphics.Color;
import android.util.Pair;

import com.nightonke.boommenu.BoomButtons.ButtonPlaceEnum;
import com.nightonke.boommenu.BoomButtons.HamButton;
import com.nightonke.boommenu.BoomButtons.SimpleCircleButton;
import com.nightonke.boommenu.BoomButtons.TextInsideCircleButton;
import com.nightonke.boommenu.BoomButtons.TextOutsideCircleButton;
import com.nightonke.boommenu.Piece.PiecePlaceEnum;
import com.nightonke.boommenu.Util;

import java.util.ArrayList;
import java.util.List;

import xju.dctcamera.R;

/**
 * Created by Belikovvv on 2017/8/1.
 */

public class BuilderManager {

    private static int[] imageResources = new int[]{

            R.drawable.exit,
            R.drawable.camera,
            R.drawable.comment,
            R.drawable.gallery,
            R.drawable.help,
            R.drawable.set,
            R.drawable.manage,
            R.drawable.share,

    };

    private static int imageResourceIndex = 0;

    public static int getImageResource() {
        if (imageResourceIndex >= imageResources.length)
            imageResourceIndex = 0;
        return imageResources[imageResourceIndex++];
    }

    static SimpleCircleButton.Builder getSimpleCircleButtonBuilder() {
        return new SimpleCircleButton.Builder()
                .normalImageRes(getImageResource());
    }

    static SimpleCircleButton.Builder getSquareSimpleCircleButtonBuilder() {
        return new SimpleCircleButton.Builder()
                .isRound(false)
                .shadowCornerRadius(Util.dp2px(20))
                .buttonCornerRadius(Util.dp2px(20))
                .normalImageRes(getImageResource());
    }

    public static TextInsideCircleButton.Builder getTextInsideCircleButtonBuilder() {
        return new TextInsideCircleButton.Builder()
                .normalImageRes(getImageResource())
                .normalTextRes(R.string.text_inside_circle_button_text_normal);
    }

    public static TextInsideCircleButton.Builder getSquareTextInsideCircleButtonBuilder() {
        return new TextInsideCircleButton.Builder()
                .isRound(false)
                .shadowCornerRadius(Util.dp2px(10))
                .buttonCornerRadius(Util.dp2px(10))
                .normalImageRes(getImageResource())
                .normalTextRes(R.string.text_inside_circle_button_text_normal);
    }

    static TextInsideCircleButton.Builder getTextInsideCircleButtonBuilderWithDifferentPieceColor() {
        return new TextInsideCircleButton.Builder()
                .normalImageRes(getImageResource())
                .normalTextRes(R.string.text_inside_circle_button_text_normal)
                .pieceColor(Color.WHITE);
    }

//    public static TextOutsideCircleButton.Builder getTextOutsideCircleButtonBuilder() {
//        return new TextOutsideCircleButton.Builder()
//                .normalImageRes(getImageResource())
//                .normalTextRes(R.string.text_outside_circle_button_text_normal);
//    }

    public static TextOutsideCircleButton.Builder getTextOutsideCircleButtonBuilder_Camera() {
        return new TextOutsideCircleButton.Builder()
                .normalImageRes(R.drawable.camera)
                .normalTextRes(R.string.text_outside_circle_button_text_normal_camera);
    }

    public static TextOutsideCircleButton.Builder getTextOutsideCircleButtonBuilder_Gallery() {
        return new TextOutsideCircleButton.Builder()
                .normalImageRes(R.drawable.gallery)
                .normalTextRes(R.string.text_outside_circle_button_text_normal_gallery);
    }

    public static TextOutsideCircleButton.Builder getTextOutsideCircleButtonBuilder_Set() {
        return new TextOutsideCircleButton.Builder()
                .normalImageRes(R.drawable.set)
                .normalTextRes(R.string.text_outside_circle_button_text_normal_set);
    }

    //    public static HamButton.Builder getHamButtonBuilderWithDifferentPieceColor() {
//        return new HamButton.Builder()
//                .normalImageRes(getImageResource())
//                .normalTextRes(R.string.text_ham_button_text_normal)
//                .subNormalTextRes(R.string.text_ham_button_sub_text_normal)
//                .pieceColor(Color.WHITE);
//    }

    public static HamButton.Builder getHamButtonBuilderWithDifferentPieceColor_Exit() {
        return new HamButton.Builder()
                .normalImageRes(R.drawable.exit)
                .normalTextRes(R.string.text_ham_button_text_normal_exit)
                .subNormalTextRes(R.string.text_ham_button_sub_text_normal_exit)
                .pieceColor(Color.WHITE);
    }

    public static HamButton.Builder getHamButtonBuilderWithDifferentPieceColor_Share() {
        return new HamButton.Builder()
                .normalImageRes(R.drawable.share)
                .normalTextRes(R.string.text_ham_button_text_normal_share)
                .subNormalTextRes(R.string.text_ham_button_sub_text_normal_share)
                .pieceColor(Color.WHITE);
    }

    public static HamButton.Builder getHamButtonBuilderWithDifferentPieceColor_Help() {
        return new HamButton.Builder()
                .normalImageRes(R.drawable.help)
                .normalTextRes(R.string.text_ham_button_text_normal_help)
                .subNormalTextRes(R.string.text_ham_button_sub_text_normal_help)
                .pieceColor(Color.WHITE);
    }

    public static HamButton.Builder getHamButtonBuilderWithDifferentPieceColor_Comment() {
        return new HamButton.Builder()
                .normalImageRes(R.drawable.comment)
                .normalTextRes(R.string.text_ham_button_text_normal_comment)
                .subNormalTextRes(R.string.text_ham_button_sub_text_normal_comment)
                .pieceColor(Color.WHITE);
    }
    static TextOutsideCircleButton.Builder getSquareTextOutsideCircleButtonBuilder() {
        return new TextOutsideCircleButton.Builder()
                .isRound(false)
                .shadowCornerRadius(Util.dp2px(15))
                .buttonCornerRadius(Util.dp2px(15))
                .normalImageRes(getImageResource())
                .normalTextRes(R.string.text_outside_circle_button_text_normal);
    }

    static TextOutsideCircleButton.Builder getTextOutsideCircleButtonBuilderWithDifferentPieceColor() {
        return new TextOutsideCircleButton.Builder()
                .normalImageRes(getImageResource())
                .normalTextRes(R.string.text_outside_circle_button_text_normal)
                .pieceColor(Color.WHITE);
    }

    static HamButton.Builder getHamButtonBuilder() {
        return new HamButton.Builder()
                .normalImageRes(getImageResource())
                .normalTextRes(R.string.text_ham_button_text_normal)
                .subNormalTextRes(R.string.text_ham_button_sub_text_normal);
    }

    static HamButton.Builder getHamButtonBuilder(String text, String subText) {
        return new HamButton.Builder()
                .normalImageRes(getImageResource())
                .normalText(text)
                .subNormalText(subText);
    }

    static HamButton.Builder getPieceCornerRadiusHamButtonBuilder() {
        return new HamButton.Builder()
                .normalImageRes(getImageResource())
                .normalTextRes(R.string.text_ham_button_text_normal)
                .subNormalTextRes(R.string.text_ham_button_sub_text_normal);
    }



    static List<String> getCircleButtonData(ArrayList<Pair> piecesAndButtons) {
        List<String> data = new ArrayList<>();
        for (int p = 0; p < PiecePlaceEnum.values().length - 1; p++) {
            for (int b = 0; b < ButtonPlaceEnum.values().length - 1; b++) {
                PiecePlaceEnum piecePlaceEnum = PiecePlaceEnum.getEnum(p);
                ButtonPlaceEnum buttonPlaceEnum = ButtonPlaceEnum.getEnum(b);
                if (piecePlaceEnum.pieceNumber() == buttonPlaceEnum.buttonNumber()
                        || buttonPlaceEnum == ButtonPlaceEnum.Horizontal
                        || buttonPlaceEnum == ButtonPlaceEnum.Vertical) {
                    piecesAndButtons.add(new Pair<>(piecePlaceEnum, buttonPlaceEnum));
                    data.add(piecePlaceEnum + " " + buttonPlaceEnum);
                    if (piecePlaceEnum == PiecePlaceEnum.HAM_1
                            || piecePlaceEnum == PiecePlaceEnum.HAM_2
                            || piecePlaceEnum == PiecePlaceEnum.HAM_3
                            || piecePlaceEnum == PiecePlaceEnum.HAM_4
                            || piecePlaceEnum == PiecePlaceEnum.HAM_5
                            || piecePlaceEnum == PiecePlaceEnum.HAM_6
                            || piecePlaceEnum == PiecePlaceEnum.Share
                            || piecePlaceEnum == PiecePlaceEnum.Custom
                            || buttonPlaceEnum == ButtonPlaceEnum.HAM_1
                            || buttonPlaceEnum == ButtonPlaceEnum.HAM_2
                            || buttonPlaceEnum == ButtonPlaceEnum.HAM_3
                            || buttonPlaceEnum == ButtonPlaceEnum.HAM_4
                            || buttonPlaceEnum == ButtonPlaceEnum.HAM_5
                            || buttonPlaceEnum == ButtonPlaceEnum.HAM_6
                            || buttonPlaceEnum == ButtonPlaceEnum.Custom) {
                        piecesAndButtons.remove(piecesAndButtons.size() - 1);
                        data.remove(data.size() - 1);
                    }
                }
            }
        }
        return data;
    }

    static List<String> getHamButtonData(ArrayList<Pair> piecesAndButtons) {
        List<String> data = new ArrayList<>();
        for (int p = 0; p < PiecePlaceEnum.values().length - 1; p++) {
            for (int b = 0; b < ButtonPlaceEnum.values().length - 1; b++) {
                PiecePlaceEnum piecePlaceEnum = PiecePlaceEnum.getEnum(p);
                ButtonPlaceEnum buttonPlaceEnum = ButtonPlaceEnum.getEnum(b);
                if (piecePlaceEnum.pieceNumber() == buttonPlaceEnum.buttonNumber()
                        || buttonPlaceEnum == ButtonPlaceEnum.Horizontal
                        || buttonPlaceEnum == ButtonPlaceEnum.Vertical) {
                    piecesAndButtons.add(new Pair<>(piecePlaceEnum, buttonPlaceEnum));
                    data.add(piecePlaceEnum + " " + buttonPlaceEnum);
                    if (piecePlaceEnum.getValue() < PiecePlaceEnum.HAM_1.getValue()
                            || piecePlaceEnum == PiecePlaceEnum.Share
                            || piecePlaceEnum == PiecePlaceEnum.Custom
                            || buttonPlaceEnum.getValue() < ButtonPlaceEnum.HAM_1.getValue()) {
                        piecesAndButtons.remove(piecesAndButtons.size() - 1);
                        data.remove(data.size() - 1);
                    }
                }
            }
        }
        return data;
    }

    private static BuilderManager ourInstance = new BuilderManager();

    public static BuilderManager getInstance() {
        return ourInstance;
    }

    private BuilderManager() {
    }
}